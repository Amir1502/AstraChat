package com.folzi.astrachat.core

import kotlinx.serialization.Serializable

/**
 * Chat message attachment. Text-like files carry inline [text] and persist with the message;
 * images carry transient [base64] that is never persisted — it is re-read from app-private
 * storage at send time and dropped when the row is written to Room or backups.
 */
@Serializable
data class Attachment(
    val id: String, val name: String, val mimeType: String, val sizeBytes: Long,
    val text: String = "", val base64: String = "",
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

enum class AttachmentKind { IMAGE, TEXT, UNSUPPORTED }

const val MAX_ATTACHMENTS_PER_MESSAGE = 5
const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
const val MAX_TEXT_BYTES = 200L * 1024
/** Budget of decoded image bytes per outbound request; older history images beyond it degrade to a marker. */
const val MAX_REQUEST_IMAGE_BYTES = 16L * 1024 * 1024

private val SUPPORTED_IMAGES = setOf("image/png", "image/jpeg", "image/webp", "image/gif")
private val TEXT_MIMES = setOf(
    "application/json", "application/xml", "text/xml", "application/csv", "text/csv",
    "application/javascript", "text/javascript", "application/x-yaml", "application/yaml",
    "application/x-sh", "text/x-shellscript", "application/sql", "application/toml",
)
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "csv", "tsv",
    "log", "html", "htm", "css", "js", "mjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "rb", "go",
    "rs", "c", "h", "cpp", "hpp", "cs", "php", "sh", "bash", "ps1", "bat", "sql", "gradle", "properties",
    "vue", "svelte", "dart", "swift", "lua", "pl", "r", "scala", "groovy", "dockerfile",
)
private val EXT_IMAGE_MIMES = mapOf(
    "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "webp" to "image/webp", "gif" to "image/gif",
)

/** Strips any path components a content provider may include and caps the display name. */
fun attachmentName(name: String): String {
    val trimmed = name.substringAfterLast('/').substringAfterLast('\\').trim()
    return trimmed.ifBlank { "файл" }.take(120)
}

/** Normalizes a declared MIME type; generic/blank types fall back to the file extension. */
fun resolveMimeType(declared: String, name: String): String {
    val mime = declared.lowercase().substringBefore(';').trim()
    if (mime.isNotBlank() && mime != "application/octet-stream") return mime
    val ext = name.substringAfterLast('.', "").lowercase()
    return EXT_IMAGE_MIMES[ext] ?: if (ext in TEXT_EXTENSIONS) "text/plain" else mime
}

fun classifyAttachment(mimeType: String): AttachmentKind = when {
    mimeType in SUPPORTED_IMAGES -> AttachmentKind.IMAGE
    mimeType.startsWith("text/") || mimeType in TEXT_MIMES -> AttachmentKind.TEXT
    else -> AttachmentKind.UNSUPPORTED
}

/** Plain, fence-free wrapper so file content can never break Markdown framing. */
fun attachmentBlock(a: Attachment): String = "--- Файл: ${a.name} (${a.mimeType}) ---\n${a.text}\n--- Конец файла: ${a.name} ---"

fun attachmentImageFallback(a: Attachment): String = "[Изображение «${a.name}» недоступно]"

fun attachmentDataUri(a: Attachment): String = "data:${a.mimeType};base64,${a.base64}"

/** Rough token cost for the context-window guard: images by size (≈1 token per 3 KB), text by estimate. */
fun attachmentTokens(a: Attachment): Long =
    if (a.isImage) (a.sizeBytes + 2999) / 3000 else TokenMath.estimate(attachmentBlock(a)) + 4

/** Lenient parse of persisted attachments JSON; a corrupt row must never crash the app. */
fun parseAttachments(value: String): List<Attachment> =
    if (value.isBlank()) emptyList() else runCatching { json.decodeFromString<List<Attachment>>(value) }.getOrDefault(emptyList())

/** Strips transient image data before the row is persisted; text content stays (it is the payload). */
fun Attachment.forStorage(): Attachment = if (base64.isEmpty()) this else copy(base64 = "")
