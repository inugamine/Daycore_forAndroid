package com.inugamine.daycore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inugamine.daycore.ui.screen.LibraryScreen
import com.inugamine.daycore.ui.screen.PlayerScreen
import com.inugamine.daycore.ui.theme.DaycoreTheme
import com.inugamine.daycore.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DaycoreTheme {
                DaycoreApp()
            }
        }
    }
}

@Composable
fun DaycoreApp(viewModel: PlayerViewModel = viewModel()) {
    val navController = rememberNavController()

    // --- パーミッション ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadMusicLibrary()
    }

    // ファイルインポート用ランチャー
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { viewModel.importFile(it) }
    }

    // 初回起動時にパーミッションチェック
    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val context = viewModel.getApplication<android.app.Application>()
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.loadMusicLibrary()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    NavHost(navController = navController, startDestination = "player") {
        composable("player") {
            PlayerScreen(
                viewModel = viewModel,
                onOpenLibrary = { navController.navigate("library") }
            )
        }
        composable("library") {
            LibraryScreen(
                viewModel = viewModel,
                onTrackSelected = { navController.popBackStack() },
                onImportFile = {
                    filePickerLauncher.launch(arrayOf("audio/*"))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
