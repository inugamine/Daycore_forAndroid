package com.inugamine.daycore.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.inugamine.daycore.model.AudioPreset
import com.inugamine.daycore.ui.component.DaycoreSlider
import com.inugamine.daycore.ui.theme.*
import com.inugamine.daycore.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onOpenLibrary: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val pitch by viewModel.pitch.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isShuffled by viewModel.isShuffled.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DaycoreBackground,
                        DaycoreBackground.copy(alpha = 0.95f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ヘッダー
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daycore", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = DaycoreTextPrimary)
                IconButton(onClick = onOpenLibrary) {
                    Icon(Icons.Default.LibraryMusic, "ライブラリ", tint = DaycoreAccent)
                }
            }

            if (currentTrack != null) {
                val track = currentTrack!!

                Spacer(modifier = Modifier.weight(0.5f))

                // アートワーク代替
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DaycoreSurface,
                    shadowElevation = 16.dp,
                    modifier = Modifier.size(260.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = DaycoreAccent,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // トラック情報
                Text(track.title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = DaycoreTextPrimary, maxLines = 1)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium,
                    color = DaycoreTextSecondary, maxLines = 1)

                Spacer(modifier = Modifier.height(24.dp))

                // シークバー
                SeekBar(
                    position = position,
                    duration = duration,
                    onSeek = { viewModel.seekTo(it) },
                    formatTime = { viewModel.formatTime(it) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 再生コントロール
                PlaybackControls(
                    isPlaying = isPlaying,
                    isShuffled = isShuffled,
                    repeatMode = repeatMode,
                    onToggle = { viewModel.togglePlayPause() },
                    onSkipBack = { viewModel.seekTo((position - 15000).coerceAtLeast(0)) },
                    onSkipForward = { viewModel.seekTo((position + 15000).coerceAtMost(duration)) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeatMode() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // プリセットセレクタ
                PresetSelector(
                    selected = selectedPreset,
                    onSelect = { viewModel.selectPreset(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // パラメータスライダー
                DaycoreSlider(
                    label = "Speed",
                    value = speed,
                    onValueChange = { viewModel.setSpeed(it) },
                    valueRange = 0.25f..2.0f,
                    displayFormat = "%.2fx"
                )
                Spacer(modifier = Modifier.height(8.dp))
                DaycoreSlider(
                    label = "Pitch",
                    value = pitch,
                    onValueChange = { viewModel.setPitch(it) },
                    valueRange = -12f..12f,
                    displayFormat = "%+.1f st"
                )

                Spacer(modifier = Modifier.weight(1f))

            } else {
                // Empty State
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.GraphicEq, null, tint = DaycoreAccent,
                    modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(20.dp))
                Text("Daycore", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = DaycoreTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("曲を選んで Daycore の世界へ", color = DaycoreTextSecondary)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onOpenLibrary,
                    colors = ButtonDefaults.buttonColors(containerColor = DaycoreAccent),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Default.LibraryMusic, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ライブラリを開く")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    formatTime: (Long) -> String
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableFloatStateOf(0f) }
    val sliderValue = if (isSeeking) seekPos else position.toFloat()

    Column {
        Slider(
            value = sliderValue,
            onValueChange = {
                isSeeking = true
                seekPos = it
            },
            onValueChangeFinished = {
                onSeek(seekPos.toLong())
                isSeeking = false
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = DaycoreAccent,
                activeTrackColor = DaycoreAccent,
                inactiveTrackColor = DaycoreSurfaceLight
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(if (isSeeking) seekPos.toLong() else position),
                style = MaterialTheme.typography.labelSmall, color = DaycoreTextMuted)
            Text(formatTime(duration),
                style = MaterialTheme.typography.labelSmall, color = DaycoreTextMuted)
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isShuffled: Boolean,
    repeatMode: Int,
    onToggle: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(Icons.Default.Shuffle, "シャッフル",
                tint = if (isShuffled) DaycoreAccent else DaycoreTextMuted,
                modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onSkipBack) {
            Icon(Icons.Default.Replay10, "15秒戻る", tint = DaycoreTextPrimary,
                modifier = Modifier.size(32.dp))
        }
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = DaycoreAccent)
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "一時停止" else "再生",
                modifier = Modifier.size(36.dp)
            )
        }
        IconButton(onClick = onSkipForward) {
            Icon(Icons.Default.Forward10, "15秒進む", tint = DaycoreTextPrimary,
                modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = onToggleRepeat) {
            Icon(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                "リピート",
                tint = if (repeatMode != Player.REPEAT_MODE_OFF) DaycoreAccent else DaycoreTextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PresetSelector(
    selected: AudioPreset,
    onSelect: (AudioPreset) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(AudioPreset.ALL) { preset ->
            val isSelected = preset.id == selected.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(preset) },
                label = { Text(preset.name, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    val icon = when (preset.id) {
                        "original" -> Icons.Default.GraphicEq
                        "daycore_soft" -> Icons.Default.BrightnessLow
                        "daycore" -> Icons.Default.LightMode
                        "daycore_deep" -> Icons.Default.WbSunny
                        "nightcore" -> Icons.Default.DarkMode
                        else -> Icons.Default.MusicNote
                    }
                    Icon(icon, null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DaycoreAccent,
                    selectedLabelColor = DaycoreTextPrimary,
                    containerColor = DaycoreSurface,
                    labelColor = DaycoreTextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = DaycoreDivider,
                    selectedBorderColor = DaycoreAccent,
                    enabled = true,
                    selected = isSelected
                )
            )
        }
    }
}
