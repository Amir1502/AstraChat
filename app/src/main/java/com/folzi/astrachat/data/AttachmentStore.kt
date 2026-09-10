package com.folzi.astrachat.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.folzi.astrachat.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports chat attachments picked through SAF into app-private storage. Text content stays inline
 * in the Attachment (persisted with the message row); image bytes live in a private file and are
 * re-read as base64 only at send time — base64 is never written to Room or backups.
 * Supported kinds and limits are declared in core/Attachments.kt; failures are IllegalArgumentException
 * with user-facing Russian text or SafeFailure.
 */
@Singleton
class AttachmentStore @Inject constructor(@ApplicationContext private val context: Context) {
    private fun dir(): File = File(context.filesDir, "attachments").apply { mkdirs() }

    suspend fun import(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val name = attachmentName(queryDisplayName(uri))
        val mime = resolveMimeType(context.contentResolver.getType(uri).orEmpty(), name)
        val kind = classifyAttachment(mime)
        require(kind != AttachmentKind.UNSUPPORTED) {
            "Тип файла не поддерживается. Прикрепите изображение (PNG, JPEG, WebP, GIF) или текстовый файл."
        }
        val limit = if (kind == AttachmentKind.IMAGE) MAX_IMAGE_BYTES else MAX_TEXT_BYTES
        val bytes = context.contentResolver.openInputStream(uri)?.use { readCapped(it, limit) }
            ?: throw SafeFailure(FailureKind.STORAGE)
        require(bytes.isNotEmpty()) { "Файл пуст." }
        val id = newId()
        if (kind == AttachmentKind.IMAGE) {
            File(dir(), id).writeBytes(bytes)
            Attachment(id, name, mime, bytes.size.toLong(), base64 = Base64.encodeToString(bytes, Base64.NO_WRAP))
        } else {
            Attachment(id, name, mime, bytes.size.toLong(), text = bytes.decodeToString())
        }
    }

    /** Refills image base64 from app-private storage; a missing file stays empty and the codec degrades it to a text marker. */
    suspend fun load(attachment: Attachment): Attachment =
        if (!attachment.isImage || attachment.base64.isNotEmpty()) attachment
        else withContext(Dispatchers.IO) {
            val file = File(dir(), attachment.id)
            if (!file.isFile || file.length() > MAX_IMAGE_BYTES) attachment
            else attachment.copy(base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) { File(dir(), id).delete(); Unit }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) return cursor.getString(index)
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun readCapped(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            require(output.size() + read <= limit) { "Файл больше ${limit / 1024 / 1024} МБ — уменьшите его или выберите другой." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
