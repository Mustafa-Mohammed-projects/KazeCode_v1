package com.kaze.code.core

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.chaquo.python.Python
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

enum class Screen { Splash, Home, Ide, Pip, Preview }

class OpenTab(file: File, text: String) {
    var file by mutableStateOf(file)
    var content by mutableStateOf(text)
    var saved by mutableStateOf(text)
    val dirty get() = content != saved
}

data class PreviewReq(
    val title: String,
    val url: String? = null,
    val html: String? = null,
    val base: String? = null,
    val nonce: Long = System.nanoTime()
)

class IdeViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("kaze", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    var screen by mutableStateOf(Screen.Splash)
    var pipFrom = Screen.Home

    var root by mutableStateOf<File?>(null)
    val tabs = mutableStateListOf<OpenTab>()
    var active by mutableIntStateOf(-1)
    var treeVersion by mutableIntStateOf(0)
    val expanded = HashSet<String>()

    var showOutput by mutableStateOf(false)
    var outText by mutableStateOf("")
    var running by mutableStateOf(false)
    var inputPrompt by mutableStateOf<String?>(null)
    var preview by mutableStateOf<PreviewReq?>(null)
    var fontSize by mutableFloatStateOf(prefs.getFloat("font", 14f))
    var recents by mutableStateOf(loadRecents())

    private val outBuf = StringBuilder()
    private var flushPending = false
    private val inputQueue = LinkedBlockingQueue<String>()
    private var phpProc: Process? = null

    fun toast(m: String) { main.post { Toast.makeText(app, m, Toast.LENGTH_SHORT).show() } }

    // ---------- console ----------
    fun out(s: String) {
        synchronized(outBuf) {
            outBuf.append(s)
            if (outBuf.length > 200_000) outBuf.delete(0, outBuf.length - 150_000)
            if (!flushPending) {
                flushPending = true
                main.postDelayed({ synchronized(outBuf) { flushPending = false; outText = outBuf.toString() } }, 50)
            }
        }
    }
    fun clearOutput() { synchronized(outBuf) { outBuf.setLength(0); outText = "" } }

    // ---------- settings / recents ----------
    fun setFont(v: Float) { fontSize = v.coerceIn(10f, 28f); prefs.edit().putFloat("font", fontSize).apply() }
    private fun loadRecents(): List<String> =
        (prefs.getString("recents", "") ?: "").split('\n').filter { it.isNotBlank() && File(it).isDirectory }
    private fun saveRecents() { prefs.edit().putString("recents", recents.joinToString("\n")).apply() }
    fun removeRecent(p: String) { recents = recents.filter { it != p }; saveRecents() }

    // ---------- projects ----------
    fun workspace(): File {
        val ext = File(Environment.getExternalStorageDirectory(), "KazeCode")
        if (Environment.isExternalStorageManager() && (ext.exists() || ext.mkdirs())) return ext
        return File(app.filesDir, "projects").also { it.mkdirs() }
    }

    fun openProject(dir: File) {
        saveAll()
        root = dir
        tabs.clear(); active = -1; expanded.clear()
        recents = (listOf(dir.path) + recents.filter { it != dir.path }).take(12); saveRecents()
        treeVersion++
        screen = Screen.Ide
    }

    fun closeProject() {
        saveAll(); stopPhp()
        root = null; tabs.clear(); active = -1
        screen = Screen.Home
    }

    fun createProject(name: String, template: String) {
        val dir = File(workspace(), name.replace(Regex("[^A-Za-z0-9._ -]"), "_"))
        if (dir.exists()) { toast("A project with this name already exists"); return }
        dir.mkdirs()
        val main = when (template) {
            "Python" -> { File(dir, "main.py").writeText("name = input(\"Your name: \")\nprint(f\"Hello, {name}!\")\n"); "main.py" }
            "JS" -> { File(dir, "index.js").writeText("const items = [1, 2, 3].map(n => n * 2);\nconsole.log(\"Hello from KazeCode\", items);\n"); "index.js" }
            "PHP" -> { File(dir, "index.php").writeText("<?php\n\$name = \"KazeCode\";\n?>\n<!DOCTYPE html>\n<html>\n<body>\n  <h1>Hello from <?= \$name ?></h1>\n  <p>Time: <?= date('H:i:s') ?></p>\n</body>\n</html>\n"); "index.php" }
            else -> {
                File(dir, "style.css").writeText("body {\n  font-family: sans-serif;\n  background: #0d1117;\n  color: #e6edf3;\n  text-align: center;\n  padding: 40px;\n}\n")
                File(dir, "script.js").writeText("document.getElementById('btn').addEventListener('click', () => {\n  console.log('clicked');\n});\n")
                File(dir, "index.html").writeText("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n  <title>$name</title>\n  <link rel=\"stylesheet\" href=\"style.css\">\n</head>\n<body>\n  <h1>Hello, KazeCode</h1>\n  <button id=\"btn\">Click me</button>\n  <script src=\"script.js\"></script>\n</body>\n</html>\n")
                "index.html"
            }
        }
        openProject(dir)
        openFile(File(dir, main))
    }

    // ---------- tree ----------
    fun refreshTree() { treeVersion++ }
    fun toggleDir(d: File) { if (!expanded.add(d.path)) expanded.remove(d.path); treeVersion++ }

    fun createFile(dir: File, name: String) {
        val f = File(dir, name)
        runCatching { f.parentFile?.mkdirs(); f.createNewFile() }.onFailure { toast("Failed: ${it.message}"); return }
        expanded.add(dir.path); treeVersion++; openFile(f)
    }
    fun createFolder(dir: File, name: String) {
        File(dir, name).mkdirs(); expanded.add(dir.path); treeVersion++
    }
    fun rename(f: File, newName: String) {
        val t = File(f.parentFile, newName)
        if (t.exists() || !f.renameTo(t)) { toast("Rename failed"); return }
        tabs.forEach { if (it.file == f) it.file = t }
        treeVersion++
    }
    fun delete(f: File) {
        tabs.indices.reversed().forEach { if (tabs[it].file.path.startsWith(f.path)) closeTab(it, save = false) }
        f.deleteRecursively(); treeVersion++
    }

    // ---------- tabs ----------
    fun openFile(f: File) {
        val i = tabs.indexOfFirst { it.file == f }
        if (i >= 0) { active = i; return }
        if (f.length() > 2_000_000) { toast("File is too large (> 2 MB)"); return }
        val text = runCatching { f.readText() }.getOrElse { toast("Can't read file"); return }
        if (text.take(2000).contains('\u0000')) { toast("Binary files can't be edited"); return }
        tabs.add(OpenTab(f, text)); active = tabs.lastIndex
    }
    fun closeTab(i: Int, save: Boolean = true) {
        if (i !in tabs.indices) return
        if (save) saveTab(tabs[i])
        tabs.removeAt(i)
        active = if (tabs.isEmpty()) -1 else minOf(if (active > i) active - 1 else active, tabs.lastIndex)
    }
    private fun saveTab(t: OpenTab) {
        if (!t.dirty) return
        runCatching { t.file.writeText(t.content); t.saved = t.content }.onFailure { toast("Save failed: ${it.message}") }
    }
    fun saveAll() { tabs.forEach { saveTab(it) } }

    // ---------- run ----------
    fun run() {
        val tab = tabs.getOrNull(active) ?: run { toast("Open a file first"); return }
        saveAll()
        val f = tab.file
        when (Lang.of(f.name)) {
            Lang.HTML -> if (f.extension == "xml") toast("Can't run XML") else {
                preview = PreviewReq(f.name, url = Uri.fromFile(f).toString()); screen = Screen.Preview
            }
            Lang.JS -> if (f.extension == "js" || f.extension == "mjs") runJs(f) else toast("Can't run .${f.extension} files")
            Lang.PY -> runPython(f)
            Lang.PHP -> runPhp(f)
            else -> toast("Can't run .${f.extension} files")
        }
    }

    private fun runJs(f: File) {
        val code = f.readText().replace("</script", "<\\/script")
        val html = "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>" +
            "<style>body{font-family:sans-serif;background:#0d1117;color:#e6edf3;padding:12px}</style></head>" +
            "<body><div id=\"app\"></div><script>\n$code\n</script></body></html>"
        preview = PreviewReq(f.name, html = html, base = Uri.fromFile(f.parentFile).toString() + "/")
        screen = Screen.Preview
    }

    private fun runPython(f: File) {
        if (running) { toast("Already running"); return }
        showOutput = true; clearOutput(); running = true; inputQueue.clear()
        out("\u25B6 python ${f.name}\n\n")
        thread(name = "kaze-python") {
            try {
                var tries = 0
                while (!Python.isStarted() && tries++ < 100) Thread.sleep(50)
                val mod = Python.getInstance().getModule("kaze_runner")
                val write: (String) -> Unit = { s -> out(s) }
                val ask: (String) -> String = { p -> askInput(p) }
                mod.callAttr("run", f.absolutePath, Pip.libDir(app).absolutePath, write, ask)
            } catch (e: Throwable) {
                out("\n" + (e.message ?: e.toString()) + "\n")
            }
            out("\n[finished]\n")
            main.post { running = false; inputPrompt = null }
        }
    }

    private fun askInput(prompt: String): String {
        main.post { inputPrompt = prompt.ifBlank { "Input" }; showOutput = true }
        return inputQueue.take()
    }
    fun submitInput(s: String) { inputPrompt = null; inputQueue.offer(s) }

    fun stop() {
        if (!running) return
        inputPrompt = null; inputQueue.offer("")
        thread { runCatching { Python.getInstance().getModule("kaze_runner").callAttr("stop") } }
    }

    private fun runPhp(f: File) {
        val bin = File(app.applicationInfo.nativeLibraryDir, "libphp.so")
        showOutput = true; clearOutput()
        if (!bin.exists()) {
            out("PHP runtime is not bundled in this build.\nSet the PHP_BINARY_URL variable in GitHub (see README) and rebuild.\n")
            return
        }
        stopPhp()
        val under = root?.let { f.path.startsWith(it.path) } == true
        val docRoot = if (under) root!! else f.parentFile!!
        val rel = f.relativeTo(docRoot).path.split(File.separatorChar).joinToString("/") { Uri.encode(it) }
        val port = 8088
        thread(name = "kaze-php") {
            try {
                val p = ProcessBuilder(bin.path, "-S", "127.0.0.1:$port", "-t", docRoot.path)
                    .directory(docRoot).redirectErrorStream(true).start()
                phpProc = p
                thread { p.inputStream.bufferedReader().forEachLine { out(it + "\n") } }
                var ok = false
                for (i in 0 until 40) {
                    ok = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 150) }; true }.getOrDefault(false)
                    if (ok) break
                    Thread.sleep(100)
                }
                if (ok) main.post { preview = PreviewReq(f.name, url = "http://127.0.0.1:$port/$rel"); screen = Screen.Preview }
                else out("PHP server failed to start\n")
            } catch (e: Throwable) { out("PHP error: ${e.message}\n") }
        }
    }
    private fun stopPhp() { phpProc?.destroy(); phpProc = null }

    fun openPip() { pipFrom = screen; screen = Screen.Pip }

    override fun onCleared() { stopPhp(); super.onCleared() }
}
