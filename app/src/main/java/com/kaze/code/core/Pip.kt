package com.kaze.code.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class PkgInfo(val name: String, val version: String, val summary: String)

/** Minimal pip: resolves packages on PyPI and installs pure-Python wheels into app storage. */
object Pip {
    private const val PYVER = 11

    private val POPULAR = ("requests urllib3 certifi idna charset-normalizer six python-dateutil pytz tzdata beautifulsoup4 soupsieve " +
        "jinja2 flask werkzeug click colorama tqdm rich pygments markdown-it-py mdurl typing-extensions attrs pyparsing packaging " +
        "toml tomli httpx httpcore h11 anyio sniffio emoji termcolor tabulate pyfiglet faker text-unidecode humanize arrow " +
        "fuzzywuzzy python-slugify validators qrcode cowsay simplejson more-itertools toolz sympy mpmath networkx decorator " +
        "tenacity docopt fire loguru schedule python-dotenv pyjwt ecdsa rsa pyasn1 colorlog prettytable wcwidth cachetools " +
        "filelock platformdirs chardet xmltodict markdown docutils feedparser tinydb sqlalchemy telethon python-telegram-bot " +
        "pyyaml-env-tag itsdangerous blinker zipp importlib-metadata jsonschema pydantic-settings deprecated art").split(' ')

    fun libDir(ctx: Context): File = File(ctx.filesDir, "pylibs").also { it.mkdirs() }
    private fun metaFile(ctx: Context) = File(ctx.filesDir, "pip_installed.json")
    fun norm(n: String) = n.trim().lowercase().replace(Regex("[-_.]+"), "-")

    private fun readMeta(ctx: Context): JSONObject =
        runCatching { JSONObject(metaFile(ctx).readText()) }.getOrElse { JSONObject() }

    fun installed(ctx: Context): Map<String, String> {
        val m = readMeta(ctx)
        return m.keys().asSequence().sorted().associateWith { m.getJSONObject(it).optString("v") }
    }

    private fun http(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "KazeCode/1.0")
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} (${url.substringAfter("//").substringBefore('/')})")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun info(n: String): JSONObject = JSONObject(String(http("https://pypi.org/pypi/${norm(n)}/json")))

    suspend fun search(q: String): List<PkgInfo> = withContext(Dispatchers.IO) {
        val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return@withContext emptyList()
        val names = LinkedHashSet<String>()
        if (words.size == 1) names.add(norm(words[0]))
        POPULAR.filter { p -> words.all { p.contains(it) } }.take(10).forEach { names.add(it) }
        coroutineScope {
            names.map { n ->
                async {
                    runCatching {
                        val j = info(n).getJSONObject("info")
                        PkgInfo(j.optString("name", n), j.optString("version"), j.optString("summary"))
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun depName(req: String): String? {
        val spec = req.substringBefore(';').trim()
        val marker = req.substringAfter(';', "").trim()
        if (marker.contains("extra")) return null
        if (Regex("""(sys_platform|platform_system|os_name)\s*==\s*['"](win32|Windows|nt|darwin|Darwin)['"]""").containsMatchIn(marker)) return null
        Regex("""python_version\s*(<=|<|>=|>)\s*['"]3\.(\d+)['"]""").findAll(marker).forEach { m ->
            val v = m.groupValues[2].toInt()
            val ok = when (m.groupValues[1]) { "<" -> PYVER < v; "<=" -> PYVER <= v; ">" -> PYVER > v; else -> PYVER >= v }
            if (!ok) return null
        }
        return Regex("^[A-Za-z0-9][A-Za-z0-9._-]*").find(spec)?.value
    }

    private fun unzip(bytes: ByteArray, dest: File): List<String> {
        val tops = LinkedHashSet<String>()
        val base = dest.canonicalPath + File.separator
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(dest, e.name)
                if (out.canonicalPath.startsWith(base)) {
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { zin.copyTo(it) }
                    }
                    tops.add(e.name.substringBefore('/'))
                }
                e = zin.nextEntry
            }
        }
        return tops.toList()
    }

    suspend fun install(ctx: Context, name: String, log: (String) -> Unit) = withContext(Dispatchers.IO) {
        val meta = readMeta(ctx)
        val seen = HashSet<String>()
        fun go(n: String, depth: Int) {
            val key = norm(n)
            if (!seen.add(key) || depth > 6) return
            if (meta.has(key)) { log("\u2714 $key already installed"); return }
            log("\u2192 resolving $key")
            val j = info(key)
            val inf = j.getJSONObject("info")
            val ver = inf.optString("version")
            val urls = j.getJSONArray("urls")
            var pick: JSONObject? = null
            for (i in 0 until urls.length()) {
                val u = urls.getJSONObject(i)
                if (u.optString("filename").endsWith("-none-any.whl")) { pick = u; break }
            }
            if (pick == null) {
                val msg = "$key $ver has no pure-Python wheel (native packages can't be installed at runtime)"
                if (depth == 0) throw IOException(msg)
                log("\u26A0 skipped dependency: $msg"); return
            }
            log("\u2193 ${pick.optString("filename")} (${pick.optLong("size") / 1024} KB)")
            val tops = unzip(http(pick.getString("url")), libDir(ctx))
            meta.put(key, JSONObject().put("v", ver).put("files", org.json.JSONArray(tops)))
            metaFile(ctx).writeText(meta.toString())
            log("\u2714 installed $key $ver")
            val deps = inf.optJSONArray("requires_dist")
            if (deps != null) for (i in 0 until deps.length()) depName(deps.getString(i))?.let { go(it, depth + 1) }
        }
        go(name, 0)
    }

    fun uninstall(ctx: Context, name: String) {
        val meta = readMeta(ctx)
        val key = norm(name)
        val files = meta.optJSONObject(key)?.optJSONArray("files") ?: return
        for (i in 0 until files.length()) File(libDir(ctx), files.getString(i)).deleteRecursively()
        meta.remove(key)
        metaFile(ctx).writeText(meta.toString())
    }
}
