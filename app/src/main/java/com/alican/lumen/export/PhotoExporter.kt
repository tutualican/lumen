package com.alican.lumen.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.alican.lumen.gl.EglCore
import com.alican.lumen.gl.FilterPipeline
import com.alican.lumen.gl.Framebuffer
import com.alican.lumen.model.FilterParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

enum class PhotoOutputFormat(val mime: String, val extension: String, val label: String) {
    PNG("image/png", "png", "PNG · kayıpsız"),
    JPEG("image/jpeg", "jpg", "JPEG · %100"),
}

data class PhotoExportResult(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val downscaled: Boolean,
)

/**
 * Lossless photo export.
 *
 * The original is decoded at full resolution (never downsampled) and rendered
 * on the GPU by the same pipeline as the preview. Images larger than the GPU
 * texture limit are processed in padded tiles so nothing is ever resized.
 *
 * The single case where resolution is reduced is when the decoded source plus
 * the output buffer would physically not fit in the app's heap; this is
 * reported back so the UI can say so out loud instead of failing silently.
 */
class PhotoExporter(private val context: Context) {

    suspend fun export(
        uri: Uri,
        params: FilterParams,
        format: PhotoOutputFormat,
        stampBitmap: Bitmap?,
        onProgress: (Float) -> Unit,
    ): PhotoExportResult = withContext(Dispatchers.Default) {
        var downscaled = false
        val bitmap = decode(uri) { downscaled = it }
        val width = bitmap.width
        val height = bitmap.height

        val rendered = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderTiles(bitmap, rendered, params, stampBitmap, onProgress)
        bitmap.recycle()

        val tempFile = File(context.cacheDir, "lumen_export_${System.currentTimeMillis()}.${format.extension}")
        tempFile.outputStream().use { out ->
            val ok = rendered.compress(
                if (format == PhotoOutputFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                100,
                out,
            )
            if (!ok) throw IllegalStateException("Görsel kodlanamadı")
        }
        rendered.recycle()

        copyExif(uri, tempFile)
        val result = publish(tempFile, width, height, format)
        tempFile.delete()
        PhotoExportResult(result, width, height, downscaled)
    }

    private fun decode(uri: Uri, onDownscaled: (Boolean) -> Unit): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))

            val w = info.size.width
            val h = info.size.height
            val pixels = w.toLong() * h.toLong()
            // Source + output buffer + tile scratch, with headroom for the encoder.
            val available = Runtime.getRuntime().maxMemory() - 96L * 1024 * 1024
            val budgetPixels = (available / 8L).coerceAtLeast(1_000_000L)
            if (pixels > budgetPixels) {
                val scale = kotlin.math.sqrt(budgetPixels.toDouble() / pixels.toDouble())
                val tw = (w * scale).toInt().coerceAtLeast(1)
                val th = (h * scale).toInt().coerceAtLeast(1)
                decoder.setTargetSize(tw, th)
                onDownscaled(true)
            } else {
                onDownscaled(false)
            }
        }
    }

    private fun renderTiles(
        source: Bitmap,
        output: Bitmap,
        params: FilterParams,
        stampBitmap: Bitmap?,
        onProgress: (Float) -> Unit,
    ) {
        val egl = EglCore()
        val surface = egl.createOffscreenSurface()
        egl.makeCurrent(surface)
        val pipeline = FilterPipeline()
        pipeline.ensurePrograms()
        pipeline.setStampBitmap(stampBitmap)

        val maxTexture = egl.maxTextureSize()
        val pad = 48
        val tileLimit = min(maxTexture - pad * 2, 2048).coerceAtLeast(256)

        val imageW = source.width
        val imageH = source.height
        val cols = (imageW + tileLimit - 1) / tileLimit
        val rows = (imageH + tileLimit - 1) / tileLimit
        val total = cols * rows.toFloat()

        var done = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * tileLimit
                val y = row * tileLimit
                val tileW = min(tileLimit, imageW - x)
                val tileH = min(tileLimit, imageH - y)

                val padL = min(pad, x)
                val padT = min(pad, y)
                val padR = min(pad, imageW - (x + tileW))
                val padB = min(pad, imageH - (y + tileH))

                val regionX = x - padL
                val regionY = y - padT
                val regionW = tileW + padL + padR
                val regionH = tileH + padT + padB

                val region = Bitmap.createBitmap(source, regionX, regionY, regionW, regionH)
                val texture = FilterPipeline.uploadTexture(region)
                if (region !== source) region.recycle()

                val fbo = Framebuffer(regionW, regionH)
                pipeline.render(
                    srcTexture = texture,
                    useOes = false,
                    texMatrix = null,
                    params = params,
                    destFramebuffer = fbo.framebufferId,
                    destWidth = regionW,
                    destHeight = regionH,
                    flipY = false,
                    fullWidth = imageW,
                    fullHeight = imageH,
                    uvScaleX = regionW.toFloat() / imageW,
                    uvScaleY = regionH.toFloat() / imageH,
                    uvOffsetX = regionX.toFloat() / imageW,
                    uvOffsetY = regionY.toFloat() / imageH,
                )

                readTileInto(fbo, padL, regionH - padT - tileH, tileW, tileH, output, x, y)

                fbo.release()
                android.opengl.GLES30.glDeleteTextures(1, intArrayOf(texture), 0)

                done++
                onProgress(done / total)
            }
        }

        pipeline.release()
        egl.releaseSurface(surface)
        egl.release()
    }

    private fun readTileInto(
        fbo: Framebuffer,
        readX: Int,
        readY: Int,
        tileW: Int,
        tileH: Int,
        output: Bitmap,
        destX: Int,
        destY: Int,
    ) {
        val buffer = java.nio.ByteBuffer.allocateDirect(tileW * tileH * 4)
            .order(java.nio.ByteOrder.nativeOrder())
        android.opengl.GLES30.glBindFramebuffer(android.opengl.GLES30.GL_FRAMEBUFFER, fbo.framebufferId)
        android.opengl.GLES30.glReadPixels(
            readX, readY, tileW, tileH,
            android.opengl.GLES30.GL_RGBA,
            android.opengl.GLES30.GL_UNSIGNED_BYTE,
            buffer,
        )
        android.opengl.GLES30.glBindFramebuffer(android.opengl.GLES30.GL_FRAMEBUFFER, 0)
        buffer.position(0)

        val rowPixels = IntArray(tileW)
        for (row in 0 until tileH) {
            for (x in 0 until tileW) {
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                rowPixels[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            // glReadPixels rows are bottom-up; flip while writing.
            val outRow = destY + (tileH - 1 - row)
            output.setPixels(rowPixels, 0, tileW, destX, outRow, tileW, 1)
        }
    }

    private fun copyExif(sourceUri: Uri, target: File) {
        try {
            val exif = context.contentResolver.openInputStream(sourceUri)?.use { ExifInterface(it) } ?: return
            val out = ExifInterface(target.absolutePath)
            val tags = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_LENS_MODEL,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO_SPEED_RATINGS,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
            )
            tags.forEach { tag ->
                exif.getAttribute(tag)?.let { out.setAttribute(tag, it) }
            }
            // Rotation was applied to the pixels, so orientation must be reset.
            out.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            out.saveAttributes()
        } catch (_: Exception) {
            // Metadata is best-effort; the pixels are already correct.
        }
    }

    private fun publish(file: File, width: Int, height: Int, format: PhotoOutputFormat): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Lumen_${System.currentTimeMillis()}.${format.extension}")
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lumen")
            put(MediaStore.Images.Media.WIDTH, width)
            put(MediaStore.Images.Media.HEIGHT, height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Galeriye kayıt noktası açılamadı")
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out, 1 shl 20) }
        } ?: throw IllegalStateException("Çıktı akışı açılamadı")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}
