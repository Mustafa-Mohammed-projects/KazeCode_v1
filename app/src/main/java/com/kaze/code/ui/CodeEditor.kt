package com.kaze.code.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kaze.code.core.*

class EditorController { var view: CodeEditText? = null }

class CodeEditText(ctx: Context) : EditText(ctx) {
    var lang: Lang = Lang.TEXT
    var onText: ((String) -> Unit)? = null
    var onSuggest: ((Int, List<Sug>) -> Unit)? = null
    var fontSp: Float = 14f
        set(v) { field = v; setTextSize(TypedValue.COMPLEX_UNIT_SP, v); updateGutter() }

    private var ready = false
    private var guard = false
    private var lStart = 0
    private var lBefore = 0
    private var lCount = 0
    private var starts = IntArray(1)
    private var digits = 2
    private var gutterW = 0
    private val dp = ctx.resources.displayMetrics.density
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5C6773.toInt(); textAlign = Paint.Align.RIGHT; typeface = Typeface.MONOSPACE
    }
    private val gutterBg = Paint().apply { color = 0xFF12171F.toInt() }
    private val hist = ArrayList<String>()
    private var hi = -1
    private val hlRun = Runnable { Highlighter.apply(text, lang) }
    private val snapRun = Runnable { snapshot() }
    private val notifyRun = Runnable { onText?.invoke(text.toString()) }

    init {
        typeface = Typeface.MONOSPACE
        setTextColor(0xFFD7DEE9.toInt())
        setBackgroundColor(Color.TRANSPARENT)
        setHighlightColor(0x557C5CFF)
        gravity = Gravity.TOP or Gravity.START
        setHorizontallyScrolling(false)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        isVerticalScrollBarEnabled = true
        setLineSpacing(0f, 1.15f)
        fontSp = 14f
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                lStart = start; lBefore = before; lCount = count
            }
            override fun afterTextChanged(e: Editable) {
                if (!ready) return
                if (!guard) autoEdit(e)
                recomputeStarts(e)
                removeCallbacks(hlRun); postDelayed(hlRun, 120)
                removeCallbacks(snapRun); postDelayed(snapRun, 450)
                removeCallbacks(notifyRun); postDelayed(notifyRun, 250)
            }
        })
        ready = true
    }

    // ---------- public API ----------
    fun load(content: String, l: Lang) {
        lang = l
        guard = true; setText(content); guard = false
        hist.clear(); hi = -1; snapshot()
        Highlighter.apply(text, lang)
        recomputeStarts(text); updateGutter()
    }

    fun flush() { removeCallbacks(notifyRun); onText?.invoke(text.toString()) }

    fun insertAtCursor(s: String) {
        val st = selectionStart.coerceAtLeast(0)
        val en = selectionEnd.coerceAtLeast(st)
        text.replace(st, en, s)
    }

    fun applySug(prefixLen: Int, s: Sug) {
        val end = selectionStart
        if (end < 0) return
        val start = (end - prefixLen).coerceAtLeast(0)
        val str = text.toString()
        val ls = str.lastIndexOf('\n', start - 1) + 1
        var k = ls
        while (k < start && (str[k] == ' ' || str[k] == '\t')) k++
        val indent = str.substring(ls, k)
        val bar = s.insert.indexOf('|')
        val before = (if (bar >= 0) s.insert.substring(0, bar) else s.insert).replace("\n", "\n$indent")
        val after = (if (bar >= 0) s.insert.substring(bar + 1) else "").replace("\n", "\n$indent")
        guard = true
        text.replace(start, end, before + after)
        guard = false
        setSelection(start + before.length)
    }

    fun undo() {
        removeCallbacks(snapRun); snapshot()
        if (hi > 0) { hi--; restore(hist[hi]) }
    }
    fun redo() { if (hi < hist.size - 1) { hi++; restore(hist[hi]) } }

    // ---------- internals ----------
    private fun restore(t: String) {
        val sel = selectionStart.coerceAtLeast(0).coerceAtMost(t.length)
        guard = true; setText(t); guard = false
        setSelection(sel)
    }

    private fun snapshot() {
        val t = text.toString()
        if (hi >= 0 && hist[hi] == t) return
        while (hist.size > hi + 1) hist.removeAt(hist.size - 1)
        hist.add(t)
        val cap = if (t.length > 100_000) 10 else 100
        while (hist.size > cap) hist.removeAt(0)
        hi = hist.size - 1
    }

    private fun autoEdit(e: Editable) {
        if (lBefore != 0 || lCount != 1) return
        val i = lStart
        if (i < 0 || i >= e.length) return
        val c = e[i]
        guard = true
        try {
            when (c) {
                '\n' -> {
                    val str = e.toString()
                    val ls = str.lastIndexOf('\n', i - 1) + 1
                    var k = ls
                    while (k < i && (str[k] == ' ' || str[k] == '\t')) k++
                    val indent = str.substring(ls, k)
                    val prev = str.substring(ls, i).trimEnd()
                    val more = prev.endsWith("{") || prev.endsWith("[") || prev.endsWith("(") || (lang == Lang.PY && prev.endsWith(":"))
                    val extra = if (more) "    " else ""
                    val next = if (i + 1 < e.length) e[i + 1] else ' '
                    if (more && (next == '}' || next == ']' || next == ')')) {
                        e.insert(i + 1, indent + extra + "\n" + indent)
                        setSelection(i + 1 + indent.length + extra.length)
                    } else if (indent.isNotEmpty() || extra.isNotEmpty()) {
                        e.insert(i + 1, indent + extra)
                        setSelection(i + 1 + indent.length + extra.length)
                    }
                }
                '(', '[', '{' -> {
                    val close = when (c) { '(' -> ')'; '[' -> ']'; else -> '}' }
                    val n = if (i + 1 < e.length) e[i + 1] else ' '
                    if (n.isWhitespace() || n in ")]}>,;") { e.insert(i + 1, close.toString()); setSelection(i + 1) }
                }
                ')', ']', '}' -> if (i + 1 < e.length && e[i + 1] == c) e.delete(i + 1, i + 2)
                '"', '\'', '`' -> {
                    if (i + 1 < e.length && e[i + 1] == c) e.delete(i + 1, i + 2)
                    else {
                        val p = if (i > 0) e[i - 1] else ' '
                        val n = if (i + 1 < e.length) e[i + 1] else ' '
                        if (!p.isLetterOrDigit() && (n.isWhitespace() || n in ")]},;>")) { e.insert(i + 1, c.toString()); setSelection(i + 1) }
                    }
                }
            }
        } finally { guard = false }
    }

    private fun recomputeStarts(e: CharSequence) {
        var n = 1
        for (i in e.indices) if (e[i] == '\n') n++
        val a = IntArray(n)
        var k = 1
        for (i in e.indices) if (e[i] == '\n') a[k++] = i + 1
        starts = a
        val d = maxOf(2, n.toString().length)
        if (d != digits) { digits = d; updateGutter() }
    }

    private fun updateGutter() {
        numPaint.textSize = textSize * 0.8f
        gutterW = (numPaint.measureText("8") * digits + 18 * dp).toInt()
        if (paddingLeft != gutterW + (8 * dp).toInt()) {
            setPadding(gutterW + (8 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (140 * dp).toInt())
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return
        if (selStart != selEnd) { onSuggest?.invoke(0, emptyList()); return }
        post {
            val c = selectionStart
            if (c >= 0) { val (n, l) = Suggester.suggest(lang, text, c); onSuggest?.invoke(n, l) }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout
        if (l != null) {
            val top = scrollY
            val bottom = scrollY + height
            canvas.drawRect(scrollX.toFloat(), top.toFloat(), (scrollX + gutterW).toFloat(), bottom.toFloat(), gutterBg)
            numPaint.textSize = textSize * 0.8f
            val x = scrollX + gutterW - 8 * dp
            val first = l.getLineForVertical(top)
            val last = l.getLineForVertical(bottom)
            for (line in first..last) {
                val idx = java.util.Arrays.binarySearch(starts, l.getLineStart(line))
                if (idx >= 0) canvas.drawText((idx + 1).toString(), x, (l.getLineBaseline(line) + compoundPaddingTop).toFloat(), numPaint)
            }
        }
        super.onDraw(canvas)
    }
}

@Composable
fun CodeEditor(
    tab: OpenTab,
    fontSize: Float,
    ctrl: EditorController,
    onSuggest: (Int, List<Sug>) -> Unit,
    modifier: Modifier = Modifier
) {
    val holder = remember { arrayOfNulls<CodeEditText>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditText(ctx).also { v ->
                v.fontSp = fontSize
                v.load(tab.content, Lang.of(tab.file.name))
                v.onText = { tab.content = it }
                v.onSuggest = onSuggest
                holder[0] = v; ctrl.view = v
            }
        },
        update = { v -> if (v.fontSp != fontSize) v.fontSp = fontSize }
    )
    DisposableEffect(Unit) {
        onDispose {
            holder[0]?.flush()
            if (ctrl.view === holder[0]) ctrl.view = null
        }
    }
}
