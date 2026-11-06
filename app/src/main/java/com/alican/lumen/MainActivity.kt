package com.alican.lumen

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alican.lumen.ui.EditorScreen
import com.alican.lumen.ui.LumenTheme
import com.alican.lumen.ui.MediaKind

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        enableEdgeToEdge()
        setContent {
            LumenTheme {
                var selection by remember { mutableStateOf<Pair<Uri, MediaKind>?>(null) }
                val current = selection
                if (current == null) {
                    HomeScreen(onPicked = { uri, kind -> selection = uri to kind })
                } else {
                    EditorScreen(
                        uri = current.first,
                        kind = current.second,
                        onBack = { selection = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onPicked: (Uri, MediaKind) -> Unit) {
    // Primary path: the modern system photo picker. It is not present on every
    // device (Chinese ROMs, no GMS, some Android 10 builds), so every launch is
    // followed by a document-provider fallback instead of crashing.
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { onPicked(it, MediaKind.PHOTO) } }

    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { onPicked(it, MediaKind.VIDEO) } }

    val photoFallback = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { onPicked(it, MediaKind.PHOTO) } }

    val videoFallback = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { onPicked(it, MediaKind.VIDEO) } }

    var error by remember { mutableStateOf<String?>(null) }

    fun pickPhoto() {
        error = null
        try {
            photoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } catch (_: ActivityNotFoundException) {
            try {
                photoFallback.launch(arrayOf("image/*"))
            } catch (_: ActivityNotFoundException) {
                error = "Bu cihazda fotoğraf seçici bulunamadı."
            }
        } catch (e: Exception) {
            error = "Fotoğraf seçici açılamadı: ${e.message}"
        }
    }

    fun pickVideo() {
        error = null
        try {
            videoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        } catch (_: ActivityNotFoundException) {
            try {
                videoFallback.launch(arrayOf("video/*"))
            } catch (_: ActivityNotFoundException) {
                error = "Bu cihazda video seçici bulunamadı."
            }
        } catch (e: Exception) {
            error = "Video seçici açılamadı: ${e.message}"
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF08080A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "LUMEN",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFF5B44A),
                letterSpacing = 8.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Kalitesiz filtre yok. Fotoğraf tam çözünürlükte işlenir,\nvideo kaynak boyutu ve akış hızında kodlanır.",
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = Color(0xFFA9A9B6),
            )
            Spacer(Modifier.height(36.dp))

            MediaCard(
                title = "Fotoğraf",
                subtitle = "PNG kayıpsız · orijinal çözünürlük",
                colors = listOf(Color(0xFF2A2010), Color(0xFF6B4A18)),
                onClick = { pickPhoto() },
            )
            Spacer(Modifier.height(16.dp))
            MediaCard(
                title = "Video",
                subtitle = "Önizleme + yüksek bitrate dışa aktarım",
                colors = listOf(Color(0xFF101E2A), Color(0xFF1B4A5A)),
                onClick = { pickVideo() },
            )

            error?.let { message ->
                Spacer(Modifier.height(20.dp))
                Text(
                    message,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Color(0xFFFF8A80),
                )
            }
        }

        CrashCard()
    }
}

/**
 * Shows the previous crash trace so it can be copied without adb.
 */
@Composable
private fun CrashCard() {
    val context = LocalContext.current
    var trace by remember { mutableStateOf(CrashLog.read(context)) }
    val text = trace ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = Color(0xFF16161C),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Son çökme kaydı",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFB4AB),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Kopyala",
                        fontSize = 12.sp,
                        color = Color(0xFFF5B44A),
                        modifier = Modifier.clickable {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Lumen crash", text))
                        },
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Temizle",
                        fontSize = 12.sp,
                        color = Color(0xFFA9A9B6),
                        modifier = Modifier.clickable {
                            CrashLog.clear(context)
                            trace = null
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    fontSize = 8.sp,
                    lineHeight = 11.sp,
                    maxLines = 18,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFCFCFDA),
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(colors))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 12.sp, color = Color(0xCCFFFFFF))
    }
}
