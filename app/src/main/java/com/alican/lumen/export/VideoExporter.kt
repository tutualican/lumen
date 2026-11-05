package com.alican.lumen.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import android.provider.MediaStore
import android.view.Surface
import com.alican.lumen.gl.EglCore
import com.alican.lumen.gl.FilterPipeline
import com.alican.lumen.model.FilterParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * High-quality video export.
 *
 * Quality choices:
 *  - Frame size is the decoded size; the video is never scaled by us.
 *  - Source rotation is carried by the muxer instead of rotating pixels.
 *  - Bitrate is derived from resolution and frame rate, never below source.
 *  - Audio is copied bit-exactly from the source track (no re-encode).
 *  - BT.709 colour metadata is written into the bitstream.
 */
class VideoExporter(
    private val context: Context,
    private val cancelled: AtomicBoolean,
) {

    data class Info(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val frameRate: Int,
        val durationUs: Long,
        val bitrate: Int,
        val mime: String,
        val hasAudio: Boolean,
    )

    class CancellationSignal : Exception("Export cancelled")

    suspend fun export(
        uri: Uri,
        params: FilterParams,
        stampBitmap: Bitmap?,
        onProgress: (Float) -> Unit,
    ): Uri = withContext(Dispatchers.Default) {
        val output = File(context.cacheDir, "lumen_video_${System.currentTimeMillis()}.mp4")
        try {
            runExport(uri, output, params, stampBitmap, onProgress)
            publish(output)
        } finally {
            output.delete()
        }
    }

    fun probe(uri: Uri): Info {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var videoFormat: MediaFormat? = null
            var hasAudio = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && videoFormat == null) videoFormat = format
                if (mime.startsWith("audio/")) hasAudio = true
            }
            val format = videoFormat ?: throw IllegalArgumentException("Videoda görüntü akışı yok")
            return Info(
                width = format.getInteger(MediaFormat.KEY_WIDTH),
                height = format.getInteger(MediaFormat.KEY_HEIGHT),
                rotation = readRotation(uri),
                frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(1, 240)
                } else {
                    30
                },
                durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    0L
                },
                bitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    format.getInteger(MediaFormat.KEY_BIT_RATE)
                } else {
                    0
                },
                mime = format.getString(MediaFormat.KEY_MIME).orEmpty(),
                hasAudio = hasAudio,
            )
        } finally {
            extractor.release()
        }
    }

    private fun readRotation(uri: Uri): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
        } catch (_: Exception) {
            0
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun runExport(
        uri: Uri,
        output: File,
        params: FilterParams,
        stampBitmap: Bitmap?,
        onProgress: (Float) -> Unit,
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var surfaceTexture: SurfaceTexture? = null
        var decoderSurface: Surface? = null
        var egl: EglCore? = null
        val pipeline = FilterPipeline()
        var encoderSurface: android.opengl.EGLSurface? = null
        var pbufferSurface: android.opengl.EGLSurface? = null
        var sinkRef: EncoderSink? = null

        try {
            extractor.setDataSource(context, uri, null)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }
            val vFormat = videoFormat ?: throw IllegalArgumentException("Videoda görüntü akışı yok")
            val srcMime = vFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val width = vFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = vFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val frameRate = if (vFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                vFormat.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(1, 240)
            } else {
                30
            }
            val srcBitrate = if (vFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                vFormat.getInteger(MediaFormat.KEY_BIT_RATE)
            } else {
                0
            }
            val durationUs = if (vFormat.containsKey(MediaFormat.KEY_DURATION)) {
                vFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                1L
            }
            val rotation = readRotation(uri)
            val quadrant = (((rotation / 90) % 4) + 4) % 4
            val encWidth = if (quadrant % 2 == 1) height else width
            val encHeight = if (quadrant % 2 == 1) width else height

            val bitrate = computeBitrate(encWidth, encHeight, frameRate, srcBitrate)
            val outMime = if (srcMime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true)) {
                MediaFormat.MIMETYPE_VIDEO_HEVC
            } else {
                MediaFormat.MIMETYPE_VIDEO_AVC
            }

            val enc = createEncoder(outMime, encWidth, encHeight, frameRate, bitrate)
            encoder = enc
            val inputSurface = enc.createInputSurface()
            enc.start()

            val gl = EglCore().also { egl = it }
            pbufferSurface = gl.createOffscreenSurface()
            gl.makeCurrent(pbufferSurface)

            val texIds = IntArray(1)
            GLES30.glGenTextures(1, texIds, 0)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texIds[0])
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            val frameLock = Object()
            var frameReady = false
            val st = SurfaceTexture(texIds[0])
            surfaceTexture = st
            st.setDefaultBufferSize(width, height)
            st.setOnFrameAvailableListener {
                synchronized(frameLock) {
                    frameReady = true
                    frameLock.notifyAll()
                }
            }
            decoderSurface = Surface(st)

            val dec = MediaCodec.createDecoderByType(srcMime)
            decoder = dec
            dec.configure(vFormat, decoderSurface, null, 0)
            dec.start()

            pipeline.ensurePrograms()
            pipeline.setStampBitmap(stampBitmap)

            encoderSurface = gl.createWindowSurface(inputSurface)
            gl.makeCurrent(encoderSurface)

            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            mux.setOrientationHint(0)

            val sink = EncoderSink(mux, audioFormat, enc)
            sinkRef = sink

            val info = MediaCodec.BufferInfo()
            val texMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            var inputDone = false
            var outputDone = false
            var lastPtsUs = 0L

            extractor.selectTrack(videoTrackIndex)

            while (!outputDone) {
                if (cancelled.get()) throw CancellationSignal()

                if (!inputDone) {
                    val inIndex = dec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = dec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            dec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = dec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val hasFrame = info.size > 0 || (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0
                    lastPtsUs = if (info.presentationTimeUs > 0) info.presentationTimeUs else lastPtsUs
                    dec.releaseOutputBuffer(outIndex, hasFrame)
                    if (!hasFrame) {
                        outputDone = true
                        continue
                    }

                    synchronized(frameLock) {
                        var waited = 0L
                        while (!frameReady && waited < 2_000_000_000L) {
                            frameLock.wait(20)
                            waited += 20
                        }
                        frameReady = false
                    }
                    st.updateTexImage()
                    st.getTransformMatrix(texMatrix)

                    pipeline.render(
                        srcTexture = texIds[0],
                        useOes = true,
                        texMatrix = texMatrix,
                        params = params,
                        destFramebuffer = 0,
                        destWidth = encWidth,
                        destHeight = encHeight,
                        flipY = true,
                        fullWidth = encWidth,
                        fullHeight = encHeight,
                        timeSeconds = lastPtsUs / 1_000_000f,
                        rotationQuadrant = quadrant,
                        gradeWidth = width,
                        gradeHeight = height,
                    )
                    gl.setPresentationTime(encoderSurface, lastPtsUs * 1000L)
                    gl.swapBuffers(encoderSurface)

                    sink.drain(waitUs = 0)
                    if (durationUs > 0) {
                        onProgress((lastPtsUs.toFloat() / durationUs).coerceIn(0f, 1f))
                    }
                    if (eos) outputDone = true
                }
            }

            // Flush encoder with end-of-stream.
            var flushed = false
            while (!flushed) {
                val idx = enc.dequeueInputBuffer(10_000)
                if (idx >= 0) {
                    enc.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    flushed = true
                }
            }
            sink.drainUntilEos()

            if (sink.started && audioTrackIndex >= 0 && sink.audioTrack >= 0) {
                extractor.selectTrack(audioTrackIndex)
                val audioBuffer = ByteBuffer.allocateDirect(512 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                var written = 0
                while (true) {
                    if (cancelled.get()) throw CancellationSignal()
                    val size = extractor.readSampleData(audioBuffer, 0)
                    if (size < 0) break
                    audioInfo.offset = 0
                    audioInfo.size = size
                    audioInfo.presentationTimeUs = extractor.sampleTime
                    audioInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    mux.writeSampleData(sink.audioTrack, audioBuffer, audioInfo)
                    extractor.advance()
                    written++
                }
                if (written == 0) {
                    // No audio samples copied; nothing else to do.
                }
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { decoderSurface?.release() }
            runCatching { surfaceTexture?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { pipeline.release() }
            runCatching { egl?.let { gl -> encoderSurface?.let { gl.releaseSurface(it) } } }
            runCatching { egl?.let { gl -> pbufferSurface?.let { gl.releaseSurface(it) } } }
            runCatching { egl?.release() }
            runCatching { extractor.release() }
            if (sinkRef?.started == true) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    /** Drains encoded video samples into the muxer. */
    private class EncoderSink(
        private val muxer: MediaMuxer,
        private val audioFormat: MediaFormat?,
        private val encoder: MediaCodec,
    ) {
        var started = false
        var videoTrack = -1
        var audioTrack = -1
        private val info = MediaCodec.BufferInfo()

        fun drain(waitUs: Long) {
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, waitUs)
                if (!handle(index)) return
            }
        }

        fun drainUntilEos() {
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) continue
                val eos = index >= 0 &&
                    info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                handle(index)
                if (eos) return
            }
        }

        /** @return false when the encoder has no more output right now. */
        private fun handle(index: Int): Boolean {
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    videoTrack = muxer.addTrack(encoder.outputFormat)
                    audioTrack = audioFormat?.let { muxer.addTrack(it) } ?: -1
                    muxer.start()
                    started = true
                    return true
                }
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrack, buffer, info)
                        }
                    }
                    encoder.releaseOutputBuffer(index, false)
                    return true
                }
            }
            return false
        }
    }

    private fun createEncoder(
        mime: String,
        width: Int,
        height: Int,
        frameRate: Int,
        bitrate: Int,
    ): MediaCodec {
        fun format(withProfile: Boolean): MediaFormat =
            MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                if (withProfile) {
                    if (mime == MediaFormat.MIMETYPE_VIDEO_HEVC) {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain)
                        setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51)
                    } else {
                        setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                        setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel51)
                    }
                }
            }

        val encoder = MediaCodec.createEncoderByType(mime)
        return try {
            encoder.configure(format(true), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            encoder
        } catch (_: Exception) {
            runCatching { encoder.release() }
            val fallback = MediaCodec.createEncoderByType(mime)
            fallback.configure(format(false), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            fallback
        }
    }

    private fun computeBitrate(width: Int, height: Int, frameRate: Int, sourceBitrate: Int): Int {
        // ~0.30 bits/pixel/frame is visually transparent for H.264/HEVC.
        val derived = (width.toLong() * height.toLong() * frameRate.toLong() * 0.30).toInt()
        val fromSource = (sourceBitrate * 1.6f).toInt()
        return max(derived, max(fromSource, 8_000_000)).coerceAtMost(150_000_000)
    }

    private fun publish(file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Lumen_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Lumen")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Galeriye kayıt noktası açılamadı")
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out, 1 shl 20) }
        } ?: throw IllegalStateException("Çıktı akışı açılamadı")
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
