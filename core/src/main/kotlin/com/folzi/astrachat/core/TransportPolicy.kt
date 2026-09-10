package com.folzi.astrachat.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object TransportPolicy {
    fun validate(url: String, allowLocalHttp: Boolean): HttpUrl {
        val parsed = url.toHttpUrlOrNull() ?: throw SafeFailure(FailureKind.BAD_URL)
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty() || parsed.fragment != null || parsed.query != null) {
            throw SafeFailure(FailureKind.BAD_URL)
        }
        if (!parsed.isHttps && (!allowLocalHttp || !isLocalLiteral(parsed.host))) throw SafeFailure(FailureKind.INSECURE)
        return parsed
    }
    fun isLocalLiteral(host: String): Boolean {
        if (host == "localhost" || host == "::1") return true
        val parts = host.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 127 || parts[0] == 10 || (parts[0] == 192 && parts[1] == 168) || (parts[0] == 172 && parts[1] in 16..31)
    }
    fun endpoint(provider: Provider, model: Model, stream: Boolean): HttpUrl {
        val base = validate(provider.baseUrl, provider.allowLocalHttp)
        val path = provider.endpoint.ifBlank {
            when (provider.protocol) {
                Protocol.CHAT_COMPLETIONS -> "chat/completions"
                Protocol.RESPONSES -> "responses"
                Protocol.ANTHROPIC -> "messages"
                Protocol.GEMINI -> "models/${model.id.removePrefix("models/")}:${if (stream) "streamGenerateContent" else "generateContent"}"
            }
        }
        // Endpoints are relative to Base URL, never an alternative origin.
        require(!path.contains("://") && !path.startsWith("//") && !path.contains("..") && !path.contains('?') && !path.contains('#'))
        return base.newBuilder().addPathSegments(path.trimStart('/')).build()
    }
}
enum class FailureKind(val messageRu: String) {
    AUTH("Проверьте API-ключ и разрешения провайдера."),
    OFFLINE("Не удалось подключиться. Проверьте интернет или адрес локального сервера."),
    TIMEOUT("Сервер не ответил вовремя. Можно увеличить таймаут."),
    RATE_LIMIT("Превышен лимит запросов. Подождите перед повтором."),
    CONTEXT("Контекст слишком велик. Создайте новый чат или уменьшите историю."),
    MODEL("Модель или endpoint недоступны. Проверьте их в настройках провайдера."),
    BAD_URL("Некорректный Base URL. Уберите секреты, query, fragment и логин из адреса."),
    INSECURE("HTTP разрешается отдельно и только для локального адреса. Ключ и чат могут быть перехвачены."),
    SSL("Не удалось проверить защищённое соединение. Сертификат сервера не принят."),
    MCP("MCP-сервер ответил не по спецификации Streamable HTTP. Проверьте URL endpoint и поддержку сервером MCP."),
    TOOL_LOOP("Модель превысила лимит раундов вызова инструментов. Ответ не завершён; уменьшите число инструментов или упростите задачу."),
    MALFORMED("Провайдер вернул ответ в неожиданном формате."),
    TRUNCATED("Поток завершился неожиданно. Полученная часть ответа сохранена."),
    SERVER("Ошибка сервера. Частичный ответ сохранён; повторите запрос вручную."),
    INVALID("Проверьте параметры запроса, JSON и настройки модели."),
    STORAGE("Не удалось прочитать защищённое хранилище. Удалите ключ и введите его заново."),
}
class SafeFailure(val kind: FailureKind, val httpCode: Int? = null) : Exception(kind.messageRu) {
    val technical: String get() = "category=${kind.name}" + (httpCode?.let { "; HTTP $it" } ?: "")
}
fun safeFailure(error: Throwable): SafeFailure = when (error) {
    is SafeFailure -> error
    is javax.net.ssl.SSLException -> SafeFailure(FailureKind.SSL)
    is java.net.SocketTimeoutException -> SafeFailure(FailureKind.TIMEOUT)
    is java.io.IOException -> SafeFailure(FailureKind.OFFLINE)
    is IllegalArgumentException -> SafeFailure(FailureKind.INVALID)
    else -> SafeFailure(FailureKind.MALFORMED)
}
fun httpFailure(code: Int, body: String): SafeFailure {
    // Parse only a bounded machine code, never echo body/messages/URLs/headers.
    val errorCode = runCatching { json.parseToJsonElement(body.take(16384)).let { it as? kotlinx.serialization.json.JsonObject }?.obj("error")?.str("code") }.getOrNull()
    return SafeFailure(when {
        errorCode in setOf("context_length_exceeded", "context_window_exceeded") -> FailureKind.CONTEXT
        errorCode == "model_not_found" -> FailureKind.MODEL
        code == 401 || code == 403 -> FailureKind.AUTH
        code == 404 -> FailureKind.MODEL
        code == 408 || code == 504 -> FailureKind.TIMEOUT
        code == 429 -> FailureKind.RATE_LIMIT
        code == 413 -> FailureKind.CONTEXT
        code in 400..499 -> FailureKind.INVALID
        else -> FailureKind.SERVER
    }, code)
}
