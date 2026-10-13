package com.kaze.code.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.kaze.code.R
import kotlinx.coroutines.delay

/** Black background + presplash.png for 3 seconds. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(600))
        delay(2400)
        done()
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.presplash), null,
            Modifier.fillMaxWidth(0.62f).alpha(alpha.value),
            contentScale = ContentScale.Fit
        )
    }
}
