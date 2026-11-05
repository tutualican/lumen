package com.alican.lumen

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alican.lumen.ui.EditorScreen
import com.alican.lumen.ui.LumenTheme
import com.alican.lumen.ui.MediaKind

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { onPicked(it, MediaKind.PHOTO) } }

    val videoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { onPicked(it, MediaKind.VIDEO) } }

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
                onClick = {
                    photoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            MediaCard(
                title = "Video",
                subtitle = "Önizleme + yüksek bitrate dışa aktarım",
                colors = listOf(Color(0xFF101E2A), Color(0xFF1B4A5A)),
                onClick = {
                    videoLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
            )
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
