package com.alican.lumen.ui

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.alican.lumen.model.FilterCatalog
import com.alican.lumen.model.FilterParams
import com.alican.lumen.model.OriginalPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Which media the editor is working with. */
enum class MediaKind { PHOTO, VIDEO }

class EditorState {
    var kind by mutableStateOf(MediaKind.PHOTO)
    var presetId by mutableStateOf(OriginalPreset.id)
    var intensity by mutableFloatStateOf(1f)
    var exporting by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var message by mutableStateOf<String?>(null)
    var photoFormat by mutableStateOf(com.alican.lumen.export.PhotoOutputFormat.PNG)
}

@Composable
fun EditorScreen(
    uri: Uri,
    kind: MediaKind,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { EditorState().apply { this.kind = kind } }

    var previewView by remember { mutableStateOf<FilteredPreviewView?>(null) }
    var previewAspect by remember { mutableFloatStateOf(1f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var stamp by remember { mutableStateOf<Bitmap?>(null) }
    var glError by remember { mutableStateOf<String?>(null) }

    val preset = FilterCatalog.byId(state.presetId)
    val params: FilterParams = remember(state.presetId, state.intensity) {
        preset.params.atIntensity(state.intensity)
    }

    // Date stamp texture is created once; only presets that ask for it use it.
    LaunchedEffect(Unit) {
        val created = withContext(Dispatchers.Default) { StampFactory.create() }
        stamp = created
        previewView?.setStamp(created)
    }

    DisposableEffect(uri) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF08080A))) {
        Column(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { ctx ->
                    FilteredPreviewView(ctx).also { view ->
                        previewView = view
                        view.onRenderError = { message -> glError = message }
                        stamp?.let { view.setStamp(it) }
                        if (kind == MediaKind.PHOTO) {
                            scope.launch {
                                val bmp = withContext(Dispatchers.IO) { loadPreviewBitmap(context, uri) }
                                if (bmp != null) {
                                    previewAspect = bmp.width.toFloat() / bmp.height
                                    view.setPhoto(bmp)
                                }
                            }
                        } else {
                            view.withVideoSurface { surface ->
                                val mp = MediaPlayer().apply {
                                    setDataSource(context, uri)
                                    setSurface(surface)
                                    isLooping = true
                                    setVolume(0f, 0f)
                                    setOnPreparedListener {
                                        it.start()
                                        it.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                    }
                                    setOnVideoSizeChangedListener { _, w, h ->
                                        if (w > 0 && h > 0) {
                                            previewAspect = w.toFloat() / h
                                            view.setVideoAspect(previewAspect)
                                            previewView?.let { }
                                        }
                                    }
                                    prepareAsync()
                                }
                                player = mp
                            }
                        }
                    }
                },
                update = { view ->
                    view.params = params
                    view.background = null
                    stamp?.let { view.setStamp(it) }
                },
            )

            ControlPanel(
                state = state,
                kind = kind,
                onPresetSelected = { state.presetId = it.id },
                onExport = {
                    scope.launch {
                        state.exporting = true
                        state.progress = 0f
                        state.message = null
                        try {
                            val stampBitmap = stamp
                            state.message = when (kind) {
                                MediaKind.PHOTO -> {
                                    val r = com.alican.lumen.export.ExportBridge.photo(
                                        context, uri, params, stampBitmap, state.photoFormat,
                                    ) { p -> state.progress = p }
                                    if (r.downscaled) {
                                        "Kaydedildi · ${r.width}×${r.height} — cihaz belleği sınırı nedeniyle ölçeklendi"
                                    } else {
                                        "Kaydedildi · ${r.width}×${r.height} · ${state.photoFormat.label}"
                                    }
                                }
                                MediaKind.VIDEO -> {
                                    com.alican.lumen.export.ExportBridge.video(
                                        context, uri, params, stampBitmap,
                                    ) { p -> state.progress = p }
                                    "Kaydedildi · Movies/Lumen"
                                }
                            }
                        } catch (e: Exception) {
                            state.message = "Hata: ${e.message}"
                        } finally {
                            state.exporting = false
                        }
                    }
                },
                onBack = onBack,
            )
        }

        if (state.exporting) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (kind == MediaKind.PHOTO) "Tam çözünürlükte işleniyor" else "Video kodlanıyor",
                        color = Color.White,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.width(220.dp),
                    )
                }
            }
        }

        glError?.let { msg ->
            Surface(
                color = Color(0xFF3A1214),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) {
                Text(
                    "Önizleme hatası: $msg",
                    color = Color(0xFFFFCDD2),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        state.message?.let { msg ->
            Surface(
                color = Color(0xFF1C1C22),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
            ) {
                Text(
                    msg,
                    color = Color(0xFFEDEDF2),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ControlPanel(
    state: EditorState,
    kind: MediaKind,
    onPresetSelected: (com.alican.lumen.model.FilterPreset) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(color = Color(0xFF0E0E12)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹ Geri",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onBack).padding(4.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    FilterCatalog.byId(state.presetId).name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onExport,
                    enabled = !state.exporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color(0xFF1A1200),
                    ),
                ) {
                    Text("Kaydet", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(FilterCatalog.presets) { item ->
                    val selected = item.id == state.presetId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(76.dp).clickable { onPresetSelected(item) },
                    ) {
                        Box(
                            Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(
                                        item.swatch.map { Color(it) },
                                    )
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(14.dp),
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.name,
                            fontSize = 11.sp,
                            maxLines = 1,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Yoğunluk", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = state.intensity,
                    onValueChange = { state.intensity = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(
                    "${(state.intensity * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (kind == MediaKind.PHOTO) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    com.alican.lumen.export.PhotoOutputFormat.entries.forEach { format ->
                        val selected = state.photoFormat == format
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF1C1C22),
                            modifier = Modifier.clickable { state.photoFormat = format },
                        ) {
                            Text(
                                format.label,
                                fontSize = 11.sp,
                                color = if (selected) Color(0xFF1A1200)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun loadPreviewBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            // Force 8-bit sRGB: HDR sources decode to F16, which cannot be
            // uploaded with GLUtils and would also look wrong in the preview.
            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
            val longest = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longest / sample > 2000) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
    } catch (_: Exception) {
        try {
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }
        } catch (_: Exception) {
            null
        }
    }
}
