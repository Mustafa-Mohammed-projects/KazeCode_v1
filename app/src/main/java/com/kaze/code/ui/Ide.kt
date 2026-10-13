@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.kaze.code.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kaze.code.core.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun IdeScreen(vm: IdeViewModel) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctrl = remember { EditorController() }
    var sugs by remember { mutableStateOf<Pair<Int, List<Sug>>>(0 to emptyList()) }
    val flush: () -> Unit = { ctrl.view?.flush() }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { flush(); vm.saveAll() }
    LaunchedEffect(vm.active) { sugs = 0 to emptyList() }
    BackHandler {
        if (drawer.isOpen) scope.launch { drawer.close() }
        else if (vm.showOutput) vm.showOutput = false
        else { flush(); vm.closeProject() }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = KC.surface, modifier = Modifier.width(310.dp)) {
                FileTree(vm) { f -> vm.openFile(f); scope.launch { drawer.close() } }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(KC.bg)) {
            TopBar(vm, ctrl, onMenu = { scope.launch { drawer.open() } }, flush = flush)
            TabsRow(vm, flush)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val tab = vm.tabs.getOrNull(vm.active)
                if (tab == null) EmptyState() else key(tab) {
                    CodeEditor(tab, vm.fontSize, ctrl, { n, l -> sugs = n to l }, Modifier.fillMaxSize())
                }
            }
            if (vm.showOutput) OutputPanel(vm)
            if (vm.tabs.isNotEmpty()) {
                if (sugs.second.isNotEmpty()) SuggestionBar(sugs.second) { s -> ctrl.view?.applySug(sugs.first, s) }
                KeyRow { ctrl.view?.insertAtCursor(it) }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Code, null, tint = KC.violet, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Open a file from the explorer", color = KC.dim)
        Text("Swipe from the left or tap \u2630", color = KC.dim, fontSize = 12.sp)
    }
}

@Composable
private fun TopBar(vm: IdeViewModel, ctrl: EditorController, onMenu: () -> Unit, flush: () -> Unit) {
    var more by remember { mutableStateOf(false) }
    val tab = vm.tabs.getOrNull(vm.active)
    Row(Modifier.fillMaxWidth().background(KC.surface).padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Files", tint = KC.text) }
        Column(Modifier.weight(1f)) {
            Text(tab?.file?.name ?: (vm.root?.name ?: "KazeCode"), color = KC.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(vm.root?.name ?: "", color = KC.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { ctrl.view?.undo() }) { Icon(Icons.Filled.Undo, "Undo", tint = KC.text) }
        IconButton(onClick = { ctrl.view?.redo() }) { Icon(Icons.Filled.Redo, "Redo", tint = KC.text) }
        IconButton(onClick = { flush(); vm.saveAll(); vm.toast("Saved") }) { Icon(Icons.Filled.Save, "Save", tint = KC.text) }
        IconButton(onClick = { flush(); if (vm.running) vm.stop() else vm.run() }) {
            Icon(if (vm.running) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Run", tint = if (vm.running) KC.red else KC.teal, modifier = Modifier.size(28.dp))
        }
        Box {
            IconButton(onClick = { more = true }) { Icon(Icons.Filled.MoreVert, "More", tint = KC.text) }
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(text = { Text("Console") }, onClick = { more = false; vm.showOutput = !vm.showOutput })
                DropdownMenuItem(text = { Text("Python packages") }, onClick = { more = false; flush(); vm.saveAll(); vm.openPip() })
                DropdownMenuItem(text = { Text("Font size +") }, onClick = { vm.setFont(vm.fontSize + 1) })
                DropdownMenuItem(text = { Text("Font size \u2212") }, onClick = { vm.setFont(vm.fontSize - 1) })
                DropdownMenuItem(text = { Text("Close project") }, onClick = { more = false; flush(); vm.closeProject() })
            }
        }
    }
}

@Composable
private fun TabsRow(vm: IdeViewModel, flush: () -> Unit) {
    if (vm.tabs.isEmpty()) return
    LazyRow(Modifier.fillMaxWidth().background(KC.surface)) {
        itemsIndexed(vm.tabs) { i, t ->
            val sel = i == vm.active
            Row(
                Modifier
                    .background(if (sel) KC.bg else Color.Transparent)
                    .drawBehind { if (sel) drawRect(KC.violet, Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx())) }
                    .clickable { flush(); vm.active = i }
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t.file.name + if (t.dirty) " \u2022" else "", color = if (sel) KC.text else KC.dim, fontSize = 13.sp, maxLines = 1)
                IconButton(onClick = { flush(); vm.closeTab(i) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Close, "Close tab", tint = KC.dim, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun OutputPanel(vm: IdeViewModel) {
    val sc = rememberScrollState()
    LaunchedEffect(vm.outText) { sc.scrollTo(sc.maxValue) }
    Column(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF0A0E14))) {
        Row(Modifier.fillMaxWidth().background(KC.surface2).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Terminal, null, tint = KC.teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Console", color = KC.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (vm.running) { Spacer(Modifier.width(10.dp)); CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.clearOutput() }) { Text("Clear", fontSize = 12.sp) }
            IconButton(onClick = { vm.showOutput = false }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Hide", tint = KC.dim, modifier = Modifier.size(16.dp)) }
        }
        Text(
            vm.outText.ifEmpty { "Press \u25B6 to run the current file" },
            color = if (vm.outText.isEmpty()) KC.dim else Color(0xFFC9D1D9),
            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(sc).padding(10.dp)
        )
    }
    vm.inputPrompt?.let { p -> InputDialog(p) { vm.submitInput(it) } }
}

@Composable
private fun InputDialog(prompt: String, onSubmit: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        containerColor = KC.surface2,
        title = { Text(prompt, color = KC.text, fontSize = 16.sp) },
        text = { OutlinedTextField(value = v, onValueChange = { v = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSubmit(v) }) { Text("Send") } }
    )
}

@Composable
private fun SuggestionBar(list: List<Sug>, onPick: (Sug) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(KC.surface2).padding(vertical = 5.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(list) { s ->
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(KC.violet.copy(alpha = .25f)).clickable { onPick(s) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(s.label, color = KC.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}

private val KEYS = listOf("Tab", "{", "}", "(", ")", "[", "]", "<", ">", "/", ";", ":", "=", "\"", "'", "$", "_", "#", "&", "|", "!", "?", "+", "-", "*", "\\")

@Composable
private fun KeyRow(onKey: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().background(KC.surface).padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(KEYS) { k ->
            Box(
                Modifier.height(36.dp).widthIn(min = 38.dp).clip(RoundedCornerShape(8.dp)).background(KC.surface2)
                    .clickable { onKey(if (k == "Tab") "    " else k) }.padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(k, color = KC.text, fontFamily = FontFamily.Monospace, fontSize = 14.sp) }
        }
    }
}

// ---------------------------------------------------------------- file tree

private sealed class TreeDialog {
    class NewFile(val dir: File) : TreeDialog()
    class NewFolder(val dir: File) : TreeDialog()
    class Rename(val f: File) : TreeDialog()
    class Delete(val f: File) : TreeDialog()
}

private fun flatten(dir: File, depth: Int, open: Set<String>, out: MutableList<Pair<File, Int>>) {
    val kids = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
    for (k in kids) {
        out.add(k to depth)
        if (k.isDirectory && k.path in open) flatten(k, depth + 1, open, out)
    }
}

private fun fileIcon(f: File): Pair<ImageVector, Color> = when (Lang.of(f.name)) {
    Lang.HTML -> Icons.Filled.Html to Color(0xFFE8734A)
    Lang.CSS -> Icons.Filled.Css to Color(0xFF4FA3E8)
    Lang.JS -> Icons.Filled.Javascript to Color(0xFFF0DB4F)
    Lang.PHP -> Icons.Filled.Php to Color(0xFF8892BF)
    Lang.PY -> Icons.Filled.Code to Color(0xFF4B8BBE)
    Lang.TEXT -> Icons.Filled.InsertDriveFile to KC.dim
}

@Composable
private fun FileTree(vm: IdeViewModel, onOpen: (File) -> Unit) {
    val root = vm.root ?: return
    val rows = remember(vm.treeVersion, root) {
        buildList<Pair<File, Int>> { flatten(root, 0, vm.expanded, this) }
    }
    var menu by remember { mutableStateOf<File?>(null) }
    var dlg by remember { mutableStateOf<TreeDialog?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(root.name, color = KC.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { dlg = TreeDialog.NewFile(root) }) { Icon(Icons.Filled.NoteAdd, "New file", tint = KC.teal) }
            IconButton(onClick = { dlg = TreeDialog.NewFolder(root) }) { Icon(Icons.Filled.CreateNewFolder, "New folder", tint = KC.teal) }
            IconButton(onClick = { vm.refreshTree() }) { Icon(Icons.Filled.Refresh, "Refresh", tint = KC.dim) }
        }
        HorizontalDivider(color = KC.line)
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.first.path }) { (f, depth) ->
                val isDir = f.isDirectory
                val open = f.path in vm.expanded
                Box {
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { if (isDir) vm.toggleDir(f) else onOpen(f) }, onLongClick = { menu = f })
                            .padding(start = (14 + depth * 14).dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDir) Icon(if (open) Icons.Filled.FolderOpen else Icons.Filled.Folder, null, tint = KC.violet, modifier = Modifier.size(19.dp))
                        else { val (ic, col) = fileIcon(f); Icon(ic, null, tint = col, modifier = Modifier.size(19.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Text(f.name, color = KC.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = menu == f, onDismissRequest = { menu = null }) {
                        if (isDir) {
                            DropdownMenuItem(text = { Text("New file") }, onClick = { menu = null; dlg = TreeDialog.NewFile(f) })
                            DropdownMenuItem(text = { Text("New folder") }, onClick = { menu = null; dlg = TreeDialog.NewFolder(f) })
                        }
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = null; dlg = TreeDialog.Rename(f) })
                        DropdownMenuItem(text = { Text("Delete", color = KC.red) }, onClick = { menu = null; dlg = TreeDialog.Delete(f) })
                    }
                }
            }
        }
    }

    val close = { dlg = null }
    when (val d = dlg) {
        is TreeDialog.NewFile -> NameDialog("New file", "", close) { vm.createFile(d.dir, it); dlg = null }
        is TreeDialog.NewFolder -> NameDialog("New folder", "", close) { vm.createFolder(d.dir, it); dlg = null }
        is TreeDialog.Rename -> NameDialog("Rename", d.f.name, close) { vm.rename(d.f, it); dlg = null }
        is TreeDialog.Delete -> AlertDialog(
            onDismissRequest = close,
            containerColor = KC.surface2,
            title = { Text("Delete \"${d.f.name}\"?", color = KC.text) },
            text = { Text(if (d.f.isDirectory) "The folder and everything inside it will be deleted." else "This can't be undone.", color = KC.dim) },
            confirmButton = { TextButton(onClick = { vm.delete(d.f); dlg = null }) { Text("Delete", color = KC.red) } },
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
        )
        null -> {}
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KC.surface2,
        title = { Text(title, color = KC.text) },
        text = { OutlinedTextField(value = v, onValueChange = { v = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onOk(v.trim()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
