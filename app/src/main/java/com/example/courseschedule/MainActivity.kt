package com.example.courseschedule

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.courseschedule.data.BackgroundMode
import com.example.courseschedule.data.ThemeMode
import com.example.courseschedule.ui.EduImportScreen
import com.example.courseschedule.ui.ImportScreen
import com.example.courseschedule.ui.ScheduleScreen
import com.example.courseschedule.ui.ScheduleViewModel
import com.example.courseschedule.ui.SettingsScreen
import com.example.courseschedule.ui.theme.CourseScheduleTheme
import com.example.courseschedule.ui.theme.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ScheduleViewModel = viewModel()
            val data by viewModel.data.collectAsStateWithLifecycle()
            val settings = data.settings
            val accent = Color(settings.accentColorArgb)
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            CourseScheduleTheme(darkTheme = darkTheme, accentColor = accent) {
                val hazeState = rememberHazeState()
                CompositionLocalProvider(LocalHazeState provides hazeState) {
                    Box(Modifier.fillMaxSize()) {
                        // 背景源：被毛玻璃模糊的彩色背景
                        Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                            Background(
                                accent = accent,
                                dark = darkTheme,
                                mode = settings.backgroundMode,
                                colorArgb = settings.backgroundColorArgb,
                                imagePath = settings.backgroundImagePath,
                            )
                        }
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = "schedule") {
                            composable("schedule") {
                                ScheduleScreen(
                                    viewModel = viewModel,
                                    onOpenSettings = { navController.navigate("settings") },
                                    onOpenImport = { navController.navigate("import") },
                                )
                            }
                            composable("settings") {
                                SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                            }
                            composable("import") {
                                ImportScreen(
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onOpenEdu = { navController.navigate("edu") },
                                )
                            }
                            composable("edu") {
                                EduImportScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Background(
    accent: Color,
    dark: Boolean,
    mode: BackgroundMode,
    colorArgb: Long,
    imagePath: String,
) {
    when (mode) {
        BackgroundMode.GRADIENT -> {
            val secondary = if (dark) Color(0xFF4A3A7A) else Color(0xFF7A6BDB)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.55f),
                                secondary.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }
        BackgroundMode.COLOR -> Box(
            Modifier
                .fillMaxSize()
                .background(Color(colorArgb)),
        )
        BackgroundMode.IMAGE -> {
            if (imagePath.isNotBlank()) {
                BackgroundImage(imagePath)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
    }
}

@Composable
private fun BackgroundImage(path: String) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.filesDir, path)
                if (file.exists()) decodeBackground(file)?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun decodeBackground(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1440 && bounds.outHeight / (sample * 2) >= 2560) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}
