package com.inugamine.daycore

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inugamine.daycore.ui.screen.LibraryScreen
import com.inugamine.daycore.ui.screen.PlayerScreen
import com.inugamine.daycore.ui.theme.DaycoreBackground
import com.inugamine.daycore.ui.theme.DaycoreTheme
import com.inugamine.daycore.ui.theme.DaycoreDivider
import com.inugamine.daycore.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isExpanded = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

            // スマホ版（Compact）では画面回転を固定
            val context = LocalContext.current
            LaunchedEffect(isExpanded) {
                val activity = context as? Activity
                activity?.requestedOrientation = if (isExpanded) {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }

            DaycoreTheme {
                DaycoreApp(
                    isExpanded = isExpanded
                )
            }
        }
    }
}

@Composable
fun DaycoreApp(
    viewModel: PlayerViewModel = viewModel(),
    isExpanded: Boolean = false
) {
    // --- パーミッション ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadMusicLibrary()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { viewModel.importFile(it) }
    }

    LaunchedEffect(Unit) {
        val context = viewModel.getApplication<android.app.Application>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, audioPermission)
            == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadMusicLibrary()
        } else {
            permissionLauncher.launch(audioPermission)
        }
    }

    // --- キーボードショートカット（Googlebook 対応）---
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Spacebar -> { viewModel.togglePlayPause(); true }
                        Key.DirectionLeft -> {
                            val pos = viewModel.currentPosition.value
                            viewModel.seekTo((pos - 10000).coerceAtLeast(0))
                            true
                        }
                        Key.DirectionRight -> {
                            val pos = viewModel.currentPosition.value
                            val dur = viewModel.duration.value
                            viewModel.seekTo((pos + 10000).coerceAtMost(dur))
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (isExpanded) {
            // タブレット / Googlebook: 2ペインレイアウト
            TwoPaneLayout(viewModel, filePickerLauncher)
        } else {
            // スマートフォン: シングルペイン（Navigation）
            SinglePaneLayout(viewModel, filePickerLauncher)
        }
    }
}

@Composable
private fun TwoPaneLayout(
    viewModel: PlayerViewModel,
    filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 左ペイン: ライブラリ（常時表示）
        Box(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(DaycoreBackground)
        ) {
            LibraryScreen(
                viewModel = viewModel,
                onTrackSelected = { /* 2ペインなので画面遷移しない */ },
                onImportFile = { filePickerLauncher.launch(arrayOf("audio/*")) },
                onBack = { /* 2ペインでは戻るボタン不要 */ },
                showBackButton = false
            )
        }

        // 区切り線
        HorizontalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp),
            color = DaycoreDivider
        )

        // 右ペイン: プレーヤー
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            PlayerScreen(
                viewModel = viewModel,
                onOpenLibrary = { /* 2ペインではライブラリ常時表示 */ },
                showLibraryButton = false
            )
        }
    }
}

@Composable
private fun SinglePaneLayout(
    viewModel: PlayerViewModel,
    filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "library"
    ) {
        composable("library") {
            LibraryScreen(
                viewModel = viewModel,
                onTrackSelected = {
                    // 曲を選んだらプレーヤーを上に積む（戻る操作でライブラリに戻れる）
                    navController.navigate("player") { launchSingleTop = true }
                },
                onImportFile = { filePickerLauncher.launch(arrayOf("audio/*")) },
                onBack = { /* ルート画面なので戻る操作なし */ },
                showBackButton = false
            )
        }
        composable("player") {
            PlayerScreen(
                viewModel = viewModel,
                onOpenLibrary = { navController.popBackStack() }
            )
        }
    }
}
