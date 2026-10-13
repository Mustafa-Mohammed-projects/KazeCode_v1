package com.kaze.code

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.kaze.code.core.IdeViewModel
import com.kaze.code.core.Screen
import com.kaze.code.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // start the embedded Python runtime off the main thread
        Thread { if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext)) }.start()
        setContent {
            KazeTheme {
                val vm: IdeViewModel = viewModel()
                App(vm)
            }
        }
    }
}

@Composable
fun App(vm: IdeViewModel) {
    Surface(Modifier.fillMaxSize(), color = KC.bg) {
        Crossfade(vm.screen, animationSpec = tween(350), label = "screen") { s ->
            when (s) {
                Screen.Splash -> SplashScreen { vm.screen = Screen.Home }
                Screen.Home -> HomeScreen(vm)
                Screen.Ide -> IdeScreen(vm)
                Screen.Pip -> PipScreen(vm)
                Screen.Preview -> PreviewScreen(vm)
            }
        }
    }
}
