package com.inugamine.daycore.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inugamine.daycore.model.Track
import com.inugamine.daycore.ui.component.TrackRow
import com.inugamine.daycore.ui.theme.*
import com.inugamine.daycore.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    onTrackSelected: () -> Unit,
    onImportFile: () -> Unit,
    onBack: () -> Unit
) {
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val libraryTracks by viewModel.libraryTracks.collectAsState()
    val importedTracks by viewModel.importedTracks.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = DaycoreBackground,
        topBar = {
            TopAppBar(
                title = { Text("ライブラリ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "戻る")
                    }
                },
                actions = {
                    if (selectedTab == 1) {
                        IconButton(onClick = onImportFile) {
                            Icon(Icons.Default.Add, "インポート", tint = DaycoreAccent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DaycoreBackground,
                    titleContentColor = DaycoreTextPrimary,
                    navigationIconContentColor = DaycoreAccent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // タブ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(DaycoreSurface, RoundedCornerShape(10.dp))
            ) {
                listOf("ミュージック" to Icons.Default.MusicNote,
                       "インポート" to Icons.Default.Folder).forEachIndexed { index, (label, icon) ->
                    val isSelected = selectedTab == index
                    TextButton(
                        onClick = { selectedTab = index },
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) DaycoreAccent.copy(alpha = 0.3f)
                                else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(10.dp)
                            ),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (isSelected) DaycoreAccentLight else DaycoreTextSecondary
                        )
                    ) {
                        Icon(icon, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 検索バー
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("曲名・アーティスト名で検索") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = DaycoreTextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, "クリア", tint = DaycoreTextMuted)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DaycoreAccent,
                    unfocusedBorderColor = DaycoreDivider,
                    focusedContainerColor = DaycoreSurface,
                    unfocusedContainerColor = DaycoreSurface,
                    cursorColor = DaycoreAccent,
                    focusedTextColor = DaycoreTextPrimary,
                    unfocusedTextColor = DaycoreTextPrimary,
                    focusedPlaceholderColor = DaycoreTextMuted,
                    unfocusedPlaceholderColor = DaycoreTextMuted
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // トラックリスト
            val tracks = when (selectedTab) {
                0 -> filteredTracks.filter { it.source == Track.TrackSource.LIBRARY }
                1 -> filteredTracks.filter { it.source == Track.TrackSource.FILE }
                else -> emptyList()
            }

            if (tracks.isEmpty()) {
                EmptyState(
                    isImportTab = selectedTab == 1,
                    onImport = onImportFile,
                    hasLibraryAccess = libraryTracks.isNotEmpty() || selectedTab == 1
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        TrackRow(
                            track = track,
                            isPlaying = currentTrack?.id == track.id,
                            onClick = {
                                viewModel.selectTrack(track)
                                onTrackSelected()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    isImportTab: Boolean,
    onImport: () -> Unit,
    hasLibraryAccess: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (isImportTab) Icons.Default.FileDownload else Icons.Default.MusicNote,
            null,
            tint = DaycoreAccent,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (isImportTab) "音楽ファイルをインポート"
            else if (!hasLibraryAccess) "ミュージックライブラリへのアクセスを許可してください"
            else "曲が見つかりません",
            color = DaycoreTextPrimary,
            style = MaterialTheme.typography.titleMedium
        )
        if (isImportTab) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("MP3, M4A, WAV, FLAC, OGG に対応",
                color = DaycoreTextSecondary,
                style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onImport,
                colors = ButtonDefaults.buttonColors(containerColor = DaycoreAccent),
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ファイルを選択")
            }
        }
    }
}
