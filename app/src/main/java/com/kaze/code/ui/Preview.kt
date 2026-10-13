package com.kaze.code.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kaze.code.core.IdeViewModel
import com.kaze.code.core.Screen

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(vm: IdeViewModel) {
    val req = vm.preview
    if (req == null) { LaunchedEffect(Unit) { vm.screen = Screen.Ide }; return }
    var web by remember { mutableStateOf<WebView?>(null) }
    val logs: SnapshotStateList<String> = remember(req) { mutableStateListOf() }
    var console by remember { mutableStateOf(false) }

    BackHandler { if (web?.canGoBack() == true) web?.goBack() else vm.screen = Screen.Ide }

    Column(Modifier.fillMaxSize().background(KC.bg)) {
        Row(Modifier.fillMaxWidth().background(KC.surface).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = Screen.Ide }) { Icon(Icons.Filled.ArrowBack, "Back", tint = KC.text) }
            Text(req.title, color = KC.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { logs.clear(); web?.reload() }) { Icon(Icons.Filled.Refresh, "Reload", tint = KC.text) }
            IconButton(onClick = { console = !console }) {
                BadgedBox(badge = { if (logs.isNotEmpty()) Badge { Text("${logs.size}") } }) { Icon(Icons.Filled.Terminal, "Console", tint = if (console) KC.teal else KC.text) }
            }
        }
        key(req.nonce) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(AColor.WHITE)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        @Suppress("DEPRECATION") run {
                            settings.allowFileAccessFromFileURLs = true
                            settings.allowUniversalAccessFromFileURLs = true
                        }
                        webViewClient = WebViewClient()
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                                val where = m.sourceId().orEmpty().substringAfterLast('/')
                                logs.add("[${m.messageLevel().name.lowercase()}] ${m.message()}  ($where:${m.lineNumber()})")
                                return true
                            }
                        }
                        if (req.html != null) loadDataWithBaseURL(req.base, req.html, "text/html", "utf-8", null) else loadUrl(req.url ?: "about:blank")
                        web = this
                    }
                },
                onRelease = { it.destroy() }
            )
        }
        if (console) {
            val sc = rememberScrollState()
            LaunchedEffect(logs.size) { sc.scrollTo(sc.maxValue) }
            Column(Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF0A0E14))) {
                Text("Console", color = KC.dim, fontSize = 12.sp, modifier = Modifier.padding(10.dp, 6.dp))
                Text(
                    if (logs.isEmpty()) "No console output" else logs.joinToString("\n"),
                    color = Color(0xFFC9D1D9), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.fillMaxSize().verticalScroll(sc).padding(10.dp)
                )
            }
        }
    }
}
