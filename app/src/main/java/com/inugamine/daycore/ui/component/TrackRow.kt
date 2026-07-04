package com.inugamine.daycore.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.inugamine.daycore.model.Track
import com.inugamine.daycore.ui.theme.*

/**
 * トラックリストの1行
 */
@Composable
fun TrackRow(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPlaying) DaycoreAccent.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // アイコン（アートワーク）
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DaycoreSurfaceLight,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = track.artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (track.source == Track.TrackSource.LIBRARY)
                                    Icons.Default.MusicNote else Icons.Default.Folder,
                                contentDescription = null,
                                tint = DaycoreAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // トラック情報
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) DaycoreAccentLight else DaycoreTextPrimary,
                maxLines = 1
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = DaycoreTextSecondary,
                maxLines = 1
            )
        }

        // 再生時間
        Text(
            text = track.durationFormatted,
            style = MaterialTheme.typography.labelSmall,
            color = DaycoreTextMuted
        )

        // 再生中インジケータ
        if (isPlaying) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "再生中",
                tint = DaycoreAccent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
