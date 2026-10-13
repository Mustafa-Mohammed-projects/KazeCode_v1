package com.kaze.code.core

import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan

enum class Lang(val label: String, val exts: List<String>) {
    HTML("HTML", listOf("html", "htm", "xml", "svg")),
    CSS("CSS", listOf("css")),
    JS("JavaScript", listOf("js", "mjs", "json", "ts")),
    PY("Python", listOf("py")),
    PHP("PHP", listOf("php")),
    TEXT("Text", emptyList());

    companion object {
        fun of(name: String): Lang {
            val e = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { e in it.exts } ?: TEXT
        }
    }
}

private fun w(s: String) = s.split(' ').filter { it.isNotEmpty() }

object Words {
    val js = w("break case catch class const continue debugger default delete do else export extends finally for function if import in instanceof let new return super switch this throw try typeof var void while with yield async await of null true false undefined console document window Math JSON Promise Array Object String Number Date setTimeout setInterval fetch addEventListener querySelector querySelectorAll getElementById innerHTML textContent classList length push pop map filter reduce forEach includes indexOf slice splice join split toString parseInt parseFloat log error warn")
    val py = w("and as assert async await break class continue def del elif else except finally for from global if import in is lambda None nonlocal not or pass raise return True False try while with yield print len range input open str int float list dict set tuple enumerate zip map filter sorted sum min max abs isinstance type append extend items keys values format join split strip replace lower upper self __init__ __name__ __main__ Exception ValueError KeyError")
    val php = w("abstract and array as break case catch class const continue declare default do echo else elseif empty extends final finally fn for foreach function global if implements include include_once instanceof interface isset namespace new null or print private protected public require require_once return static switch throw trait try unset use var while true false strlen str_replace explode implode count array_map array_merge json_encode json_decode var_dump print_r date time isset empty htmlspecialchars file_get_contents file_put_contents")
    val css = w("color background background-color margin margin-top margin-bottom margin-left margin-right padding padding-top padding-bottom border border-radius display flex flex-direction justify-content align-items grid grid-template-columns position absolute relative fixed width height max-width min-height font-size font-family font-weight text-align line-height overflow opacity transition transform box-shadow z-index cursor gap content none auto block inline-block center solid")
    val tags = w("a abbr article aside audio b blockquote body br button canvas code div footer form h1 h2 h3 h4 h5 h6 head header hr html i iframe img input label li link main meta nav ol option p pre script section select small span strong style svg table tbody td textarea th thead title tr ul video")
    val voidTags = setOf("br", "hr", "img", "input", "link", "meta")
}

class Snip(val trigger: String, val body: String)

object Snippets {
    val js = listOf(
        Snip("fn", "function |() {\n  \n}"),
        Snip("for", "for (let i = 0; i < |; i++) {\n  \n}"),
        Snip("if", "if (|) {\n  \n}"),
        Snip("log", "console.log(|);"),
        Snip("afn", "const | = () => {\n  \n};"),
        Snip("try", "try {\n  |\n} catch (e) {\n  console.error(e);\n}")
    )
    val py = listOf(
        Snip("def", "def |():\n    pass"),
        Snip("class", "class |:\n    def __init__(self):\n        pass"),
        Snip("for", "for | in range():\n    "),
        Snip("if", "if |:\n    "),
        Snip("main", "if __name__ == \"__main__\":\n    |"),
        Snip("try", "try:\n    |\nexcept Exception as e:\n    print(e)")
    )
    val php = listOf(
        Snip("php", "<?php\n|\n?>"),
        Snip("fn", "function |() {\n    \n}"),
        Snip("foreach", "foreach (| as \$item) {\n    \n}"),
        Snip("echo", "echo |;"),
        Snip("if", "if (|) {\n    \n}")
    )
    val html = listOf(
        Snip("html5", "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n  <title>|</title>\n</head>\n<body>\n  \n</body>\n</html>"),
        Snip("script", "<script>\n  |\n</script>"),
        Snip("style", "<style>\n  |\n</style>")
    )
}

data class Sug(val label: String, val insert: String)

object Suggester {
    private fun isWord(c: Char, lang: Lang) = c.isLetterOrDigit() || c == '_' || c == '$' || (lang == Lang.CSS && c == '-')

    /** returns (prefixLength, suggestions) */
    fun suggest(lang: Lang, text: CharSequence, cursor: Int): Pair<Int, List<Sug>> {
        if (cursor <= 0 || cursor > text.length) return 0 to emptyList()
        var s = cursor
        while (s > 0 && isWord(text[s - 1], lang)) s--
        val prefix = text.subSequence(s, cursor).toString()
        if (prefix.length < 2) return 0 to emptyList()
        val out = LinkedHashMap<String, Sug>()
        fun add(label: String, ins: String) { if (out.size < 12 && label !in out) out[label] = Sug(label, ins) }
        fun starts(x: String) = x.startsWith(prefix, ignoreCase = true)

        // HTML: tag names right after "<"
        if (lang == Lang.HTML && s > 0 && text[s - 1] == '<') {
            for (t in Words.tags) if (starts(t) && t != prefix) {
                add(t, if (t in Words.voidTags) "$t |>" else "$t>|</$t>")
            }
            return prefix.length to out.values.toList()
        }
        val snips = when (lang) { Lang.JS -> Snippets.js; Lang.PY -> Snippets.py; Lang.PHP -> Snippets.php; Lang.HTML -> Snippets.html; else -> emptyList() }
        for (sn in snips) if (starts(sn.trigger)) add("\u26A1 " + sn.trigger, sn.body)
        val words = when (lang) { Lang.JS -> Words.js; Lang.PY -> Words.py; Lang.PHP -> Words.php; Lang.CSS -> Words.css; else -> emptyList() }
        for (k in words) if (starts(k) && k != prefix) add(k, k)
        // words already in the document
        val src = if (text.length > 200_000) text.subSequence(0, 200_000) else text
        val re = if (lang == Lang.PHP) Regex("\\$?[A-Za-z_][A-Za-z0-9_]*") else if (lang == Lang.CSS) Regex("[A-Za-z_-][A-Za-z0-9_-]*") else Regex("[A-Za-z_$][A-Za-z0-9_$]*")
        var n = 0
        for (m in re.findAll(src)) {
            val v = m.value
            if (v.length > prefix.length && starts(v) && v !in out) { add(v, v); if (++n >= 6) break }
        }
        return prefix.length to out.values.toList()
    }
}

class HlSpan(color: Int) : ForegroundColorSpan(color)

object Highlighter {
    private val kwC = 0xFFC678DD.toInt()
    private val strC = 0xFF98C379.toInt()
    private val numC = 0xFFD19A66.toInt()
    private val comC = 0xFF6B7686.toInt()
    private val fnC = 0xFF61AFEF.toInt()
    private val tagC = 0xFFE06C75.toInt()
    private val typeC = 0xFF56B6C2.toInt()

    private fun kw(words: List<String>) = "\\b(?:" + words.joinToString("|") + ")\\b"
    private const val STR = "\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"
    private const val NUM = "\\b\\d+(?:\\.\\d+)?\\b"
    private const val FUNC = "\\b[A-Za-z_][\\w]*(?=\\()"

    private fun r(p: String, c: Int) = Regex(p) to c

    private val cache = HashMap<Lang, List<Pair<Regex, Int>>>()

    private fun rules(l: Lang): List<Pair<Regex, Int>> = cache.getOrPut(l) {
        when (l) {
            Lang.JS -> listOf(
                r(NUM, numC), r(kw(Words.js.take(40)), kwC), r(FUNC, fnC),
                r("\\b(?:console|document|window|Math|JSON|Promise|Array|Object|String|Number|Date)\\b", typeC),
                r("$STR|`(?:\\\\.|[^`\\\\])*`", strC), r("//.*|/\\*[\\s\\S]*?\\*/", comC))
            Lang.PY -> listOf(
                r(NUM, numC), r(kw(Words.py.take(35)), kwC), r(FUNC, fnC),
                r("\\b(?:print|len|range|input|open|str|int|float|list|dict|set|tuple|self)\\b", typeC),
                r("@\\w+", typeC),
                r("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|$STR", strC), r("#.*", comC))
            Lang.PHP -> listOf(
                r(NUM, numC), r(kw(Words.php.take(52)), kwC), r(FUNC, fnC),
                r("\\$[A-Za-z_]\\w*", tagC), r("<\\?php|\\?>", kwC),
                r(STR, strC), r("//.*|#.*|/\\*[\\s\\S]*?\\*/", comC))
            Lang.HTML -> listOf(
                r("<!DOCTYPE[^>]*>", typeC), r("</?[A-Za-z][\\w:-]*", tagC), r("/?>", tagC),
                r("\\s[\\w:@-]+(?==)", numC), r(STR, strC), r("<!--[\\s\\S]*?-->", comC))
            Lang.CSS -> listOf(
                r("-?\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|s|ms)?", numC), r("#[0-9a-fA-F]{3,8}\\b", numC),
                r("[#.][A-Za-z_][\\w-]*(?=[^{}]*\\{)", tagC), r("[a-z-]+(?=\\s*:)", fnC),
                r(STR, strC), r("/\\*[\\s\\S]*?\\*/", comC))
            Lang.TEXT -> emptyList()
        }
    }

    fun apply(e: Editable, lang: Lang) {
        for (s in e.getSpans(0, e.length, HlSpan::class.java)) e.removeSpan(s)
        if (e.length > 300_000) return
        val t = e.toString()
        for ((re, color) in rules(lang)) {
            for (m in re.findAll(t)) {
                if (m.value.isEmpty()) continue
                e.setSpan(HlSpan(color), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
