@file:OptIn(ExperimentalMaterial3Api::class)

package com.kaze.code.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.code.core.IdeViewModel
import com.kaze.code.core.Pip
import com.kaze.code.core.PkgInfo
import kotlinx.coroutines.launch

@Composable
fun PipScreen(vm: IdeViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler { vm.screen = vm.pipFrom }

    var tab by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PkgInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf(Pip.installed(ctx)) }

    fun search() {
        if (q.isBlank() || loading) return
        loading = true; searched = true
        scope.launch {
            results = runCatching { Pip.search(q) }.getOrElse { log += "Search failed: ${it.message}\n"; emptyList() }
            loading = false
        }
    }
    fun install(n: String) {
        if (busy != null) return
        busy = n; log += "\n$ pip install $n\n"
        scope.launch {
            try { Pip.install(ctx, n) { log += it + "\n" } } catch (e: Exception) { log += "\u2716 ${e.message}\n" }
            installed = Pip.installed(ctx); busy = null
        }
    }

    Column(Modifier.fillMaxSize().background(KC.bg)) {
        Row(Modifier.fillMaxWidth().background(KC.surface).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.screen = vm.pipFrom }) { Icon(Icons.Filled.ArrowBack, "Back", tint = KC.text) }
            Text("Python packages", color = KC.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        TabRow(selectedTabIndex = tab, containerColor = KC.surface, contentColor = KC.teal) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Search") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Installed (${installed.size})") })
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
            if (tab == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        placeholder = { Text("e.g. requests, beautifulsoup4") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { search() }) { Icon(Icons.Filled.Search, "Search", tint = KC.teal) }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                Text(
                    "Pure-Python packages only (no numpy / pandas). Dependencies are installed automatically.",
                    color = KC.dim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (searched && !loading && results.isEmpty()) item { Text("No packages found", color = KC.dim) }
                    items(results) { p ->
                        val has = installed.containsKey(Pip.norm(p.name))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KC.surface).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${p.name}  ${p.version}", color = KC.text, fontWeight = FontWeight.SemiBold)
                                if (p.summary.isNotBlank()) Text(p.summary, color = KC.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            when {
                                busy == p.name -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                has -> Icon(Icons.Filled.CheckCircle, "Installed", tint = KC.green)
                                else -> FilledTonalButton(onClick = { install(p.name) }, enabled = busy == null) { Text("Install") }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (installed.isEmpty()) item { Text("Nothing installed yet", color = KC.dim) }
                    items(installed.entries.toList()) { (n, v) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KC.surface).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$n  $v", color = KC.text, modifier = Modifier.weight(1f))
                            IconButton(onClick = { Pip.uninstall(ctx, n); installed = Pip.installed(ctx) }) {
                                Icon(Icons.Filled.Delete, "Uninstall", tint = KC.red)
                            }
                        }
                    }
                }
            }
        }
        if (log.isNotBlank()) {
            val sc = rememberScrollState()
            LaunchedEffect(log) { sc.scrollTo(sc.maxValue) }
            Text(
                log, color = Color(0xFFC9D1D9), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().height(130.dp).background(Color(0xFF0A0E14)).verticalScroll(sc).padding(10.dp)
            )
        }
    }
}
