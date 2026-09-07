package com.folzi.astrachat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import java.util.Locale

fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Astra Chat", text))
}
fun number(value: Double, digits: Int = 1): String = if (value.isFinite()) String.format(Locale.getDefault(), "%.${digits}f", value) else "—"
@Composable
fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
}
@Composable
fun Confirm(title: String, description: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(description) },
        confirmButton = { Action("Подтвердить") { onConfirm(); onDismiss() } }, dismissButton = { Action("Отмена", onClick = onDismiss) })
}
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(top = 12.dp, end = 12.dp))
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}
@Composable
fun TextField(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
}
@Composable
fun Markdown(text: String, scale: Float) {
    val context = LocalContext.current
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val link = MaterialTheme.colorScheme.primary.toArgb()
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, href -> openLink(view, href) }
                }
            })
            .build()
    }
    val segments = remember(text) { splitCode(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            if (segment.code) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(segment.language.ifBlank { "Код" }, style = MaterialTheme.typography.labelLarge)
                            Action("Копировать код") { copy(context, segment.text) }
                        }
                        SelectionContainer {
                            Text(highlight(segment.text), fontFamily = FontFamily.Monospace, fontSize = (14 * scale).sp,
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp))
                        }
                    }
                }
            } else if (segment.text.isNotEmpty()) {
                AndroidView(factory = { ctx ->
                    TextView(ctx).apply {
                        setTextIsSelectable(true); setPadding(0, 0, 0, 0)
                        movementMethod = SelectableLinkMovementMethod()
                    }
                },
                    update = { view -> view.setTextColor(foreground); view.setLinkTextColor(link); view.textSize = 16 * scale; markwon.setMarkdown(view, segment.text) },
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
data class MarkdownSegment(val text: String, val code: Boolean, val language: String = "")
fun splitCode(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>(); var code = false; var fence = ""; var language = ""
    val buffer = StringBuilder()
    for (line in text.split('\n')) {
        val trimmed = line.trimStart()
        if (!code && (trimmed.startsWith("```") || trimmed.startsWith("~~~"))) {
            if (buffer.isNotEmpty()) segments += MarkdownSegment(buffer.toString().trimEnd('\n'), false)
            buffer.clear(); code = true
            fence = trimmed.takeWhile { it == trimmed[0] }; language = trimmed.drop(fence.length).trim()
        } else if (code && trimmed.startsWith(fence) && trimmed.all { it == fence[0] || it.isWhitespace() }) {
            segments += MarkdownSegment(buffer.toString().trimEnd('\n'), true, language); buffer.clear(); code = false
        } else buffer.append(line).append('\n')
    }
    if (buffer.isNotEmpty()) segments += MarkdownSegment(buffer.toString().trimEnd('\n'), code, language)
    return segments
}
@Composable
private fun highlight(text: String) = buildAnnotatedString {
    append(text)
    val keyword = MaterialTheme.colorScheme.primary
    val stringColor = if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) Color(0xFF83CCA0) else Color(0xFF207044)
    Regex("\\b(fun|val|var|class|return|if|else|for|while|import|package|def|const|let|function|public|private|true|false|null|None|async|await)\\b|\"(?:[^\"\\\\]|\\\\.)*\"").findAll(text).forEach { m ->
        addStyle(SpanStyle(color = if (m.value.startsWith('"')) stringColor else keyword), m.range.first, m.range.last + 1)
    }
}

private fun openLink(view: View, href: String) {
    val uri = Uri.parse(href)
    val scheme = uri.scheme
    if (scheme != "http" && scheme != "https") return
    runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

/** ArrowKeyMovementMethod keeps text selection alive; taps on link spans are consumed before delegating. */
private class SelectableLinkMovementMethod : ArrowKeyMovementMethod() {
    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.action
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            val layout = widget.layout
            if (layout != null) {
                val x = event.x - widget.totalPaddingLeft + widget.scrollX
                val y = event.y - widget.totalPaddingTop + widget.scrollY
                val offset = layout.getOffsetForHorizontal(layout.getLineForVertical(y.toInt()), x)
                val spans = buffer.getSpans(offset, offset, URLSpan::class.java)
                if (spans.isNotEmpty()) {
                    if (action == MotionEvent.ACTION_UP) spans[0].onClick(widget)
                    return true
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }
}
