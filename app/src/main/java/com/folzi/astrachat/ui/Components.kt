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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

/** Quiet text action: no fill, no pill shape, neutral ink. */
@Composable
fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.heightIn(min = 44.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** The single accented action on a screen. */
@Composable
fun PrimaryAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 44.dp),
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** Hairline sheet: the basic container for a group of content. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: Color = MaterialTheme.colorScheme.outlineVariant,
    padding: Dp = 14.dp,
    spacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = color, border = BorderStroke(1.dp, border)) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Small letterspaced caption for panel and transcript headings. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Transcript speaker tag; assistant turns carry a small accent dot. */
@Composable
fun RoleLabel(text: String, modifier: Modifier = Modifier, accented: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (accented) Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (accented) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Confirm(title: String, description: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(description, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Action("Подтвердить") { onConfirm(); onDismiss() } },
        dismissButton = { Action("Отмена", onClick = onDismiss) },
    )
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title, Modifier.padding(start = 4.dp))
        Panel { content() }
    }
}

@Composable
fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
fun TextField(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        segments.forEach { segment ->
            if (segment.code) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                segment.language.ifBlank { "Код" },
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                            Action("Копировать код") { copy(context, segment.text) }
                        }
                        SelectionContainer {
                            Text(
                                highlight(segment.text),
                                fontFamily = FontFamily.Monospace,
                                fontSize = (13.5f * scale).sp,
                                lineHeight = (20 * scale).sp,
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 2.dp, top = 6.dp, bottom = 4.dp),
                            )
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
                    update = { view ->
                        view.setTextColor(foreground); view.setLinkTextColor(link)
                        view.textSize = 15.5f * scale
                        markwon.setMarkdown(view, segment.text)
                    },
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val stringColor = if (dark) Color(0xFF9CCBA8) else Color(0xFF2F6B4F)
    Regex("//.*|/\\*[\\s\\S]*?\\*/|#.*").findAll(text).forEach { m ->
        addStyle(SpanStyle(color = muted), m.range.first, m.range.last + 1)
    }
    Regex("\\b(fun|val|var|class|return|if|else|for|while|import|package|def|const|let|function|public|private|true|false|null|None|async|await)\\b|\"(?:[^\"\\\\]|\\\\.)*\"").findAll(text).forEach { m ->
        val literal = m.value.startsWith('"')
        addStyle(SpanStyle(color = if (literal) stringColor else keyword, fontWeight = if (literal) FontWeight.Normal else FontWeight.Medium), m.range.first, m.range.last + 1)
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
