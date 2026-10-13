@file:OptIn(ExperimentalMaterial3Api::class)

package com.kaze.code.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kaze.code.core.IdeViewModel
import java.io.File

@Composable
fun HomeScreen(vm: IdeViewModel) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = Environment.isExternalStorageManager() }
    var picker by remember { mutableStateOf(false) }
    var newDlg by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(KC.violet, KC.teal))),
                    contentAlignment = Alignment.Center
                ) { Text("</>", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 18.sp) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("KazeCode", color = KC.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Write. Run. Ship.", color = KC.dim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!granted) item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KC.red.copy(alpha = .12f))
                    .border(1.dp, KC.red.copy(alpha = .4f), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("File access needed", color = KC.text, fontWeight = FontWeight.SemiBold)
                    Text("Allow access to all files to open and edit project folders.", color = KC.dim, fontSize = 12.sp)
                }
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Allow") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionCard(Icons.Filled.FolderOpen, "Open folder", "Edit an existing project", KC.teal, Modifier.weight(1f)) { picker = true }
                ActionCard(Icons.Filled.CreateNewFolder, "New project", "HTML · Python · JS · PHP", KC.violet, Modifier.weight(1f)) { newDlg = true }
            }
        }
        item {
            ActionCard(Icons.Filled.Extension, "Python packages", "Search & install with pip", KC.green, Modifier.fillMaxWidth()) { vm.openPip() }
        }
        if (vm.recents.isNotEmpty()) item {
            Text("Recent projects", color = KC.dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
        items(vm.recents) { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KC.surface)
                    .clickable { vm.openProject(File(p)) }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Folder, null, tint = KC.violet)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(File(p).name, color = KC.text, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(p, color = KC.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.removeRecent(p) }) { Icon(Icons.Filled.Close, "Remove", tint = KC.dim, modifier = Modifier.size(18.dp)) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (picker) FolderPicker(onPick = { picker = false; vm.openProject(it) }, onDismiss = { picker = false })
    if (newDlg) NewProjectDialog(onCreate = { n, t -> newDlg = false; vm.createProject(n, t) }, onDismiss = { newDlg = false })
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, sub: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.clip(shape).background(KC.surface).border(BorderStroke(1.dp, KC.line), shape).clickable(onClick = onClick).padding(16.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = KC.text, fontWeight = FontWeight.SemiBold)
        Text(sub, color = KC.dim, fontSize = 12.sp)
    }
}

@Composable
private fun NewProjectDialog(onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("MyProject") }
    var tpl by remember { mutableStateOf("HTML") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KC.surface2,
        title = { Text("New project", color = KC.text) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Project name") })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("HTML", "Python", "JS", "PHP").forEach { t ->
                        FilterChip(selected = tpl == t, onClick = { tpl = t }, label = { Text(t) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim(), tpl) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FolderPicker(onPick: (File) -> Unit, onDismiss: () -> Unit) {
    var dir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = KC.bg) {
            Column {
                Row(Modifier.fillMaxWidth().background(KC.surface).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { dir.parentFile?.let { dir = it } }) { Icon(Icons.Filled.ArrowUpward, "Up", tint = KC.text) }
                    Text(dir.path, color = KC.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = KC.text) }
                }
                val kids = remember(dir) {
                    dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() }
                }
                LazyColumn(Modifier.weight(1f)) {
                    if (kids == null) item { Text("Can't read this folder. Grant file access first.", color = KC.dim, modifier = Modifier.padding(16.dp)) }
                    else if (kids.isEmpty()) item { Text("No sub-folders", color = KC.dim, modifier = Modifier.padding(16.dp)) }
                    else items(kids) { k ->
                        Row(Modifier.fillMaxWidth().clickable { dir = k }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Folder, null, tint = KC.violet)
                            Spacer(Modifier.width(12.dp))
                            Text(k.name, color = KC.text)
                        }
                    }
                }
                Button(
                    onClick = { onPick(dir) },
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                ) { Text("Open \"${dir.name}\"") }
            }
        }
    }
}
