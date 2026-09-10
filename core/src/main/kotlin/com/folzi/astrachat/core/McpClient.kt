package com.folzi.astrachat.core

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val TERMINATE_TIMEOUT_MS = 2000L
private const val MAX_SESSION_ID = 512
private const val MAX_PAGES = 20
private val BLOCKED_HEADERS = setOf("host", "content-length", "connection", "transfer-encoding", "accept", "content-type", "mcp-session-id", "mcp-protocol-version")

/** MCP client for the Streamable HTTP transport (protocol revision 2025-06-18).
 * One-shot calls run initialize -> operation -> DELETE; openSession() retains the session until close(). */
class McpClient(private val base: OkHttpClient = OkHttpClient()) {
    private fun http(server: McpServer) = base.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(server.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .callTimeout(server.timeoutSeconds.toLong(), TimeUnit.SECONDS).build()

    suspend fun discover(server: McpServer, credentials: Credentials): McpDiscovery = withSession(server, credentials) {
        val s = session ?: throw SafeFailure(FailureKind.MCP)
        McpDiscovery(server.id, s,
            if (s.capabilities.tools) list("tools/list", "tools", ::parseMcpTool) else emptyList(),
            if (s.capabilities.resources) list("resources/list", "resources") { o -> o.str("uri").takeIf(String::isNotBlank)?.let { McpResource(it, o.str("name"), o.str("description"), o.str("mimeType")) } } else emptyList(),
            if (s.capabilities.prompts) list("prompts/list", "prompts") { o -> o.str("name").takeIf(String::isNotBlank)?.let { McpPrompt(it, o.str("title"), o.str("description"), o.arr("arguments").filterIsInstance<JsonObject>().map { a -> McpPromptArgument(a.str("name"), a.str("description"), a["required"] == JsonPrimitive(true)) }.filter { a -> a.name.isNotBlank() }) } } else emptyList())
    }

    suspend fun readResource(server: McpServer, credentials: Credentials, uri: String): String {
        require(uri.isNotBlank() && uri.length <= 2048)
        return withSession(server, credentials) {
            val result = try { request("resources/read", buildJsonObject { put("uri", uri) }) } catch (missing: Rpc.NotFound) { throw SafeFailure(FailureKind.MCP) }
            val text = result.arr("contents").filterIsInstance<JsonObject>().firstNotNullOfOrNull { it.str("text").takeIf(String::isNotEmpty) } ?: throw SafeFailure(FailureKind.MCP)
            if (text.length > MAX_RESOURCE_CHARS) throw SafeFailure(FailureKind.MCP)
            text
        }
    }

    suspend fun getPrompt(server: McpServer, credentials: Credentials, name: String, arguments: Map<String, String>): List<McpPromptMessage> {
        require(name.isNotBlank())
        return withSession(server, credentials) {
            val params = buildJsonObject {
                put("name", name)
                if (arguments.isNotEmpty()) putJsonObject("arguments") { arguments.forEach { (k, v) -> put(k, v) } }
            }
            val result = try { request("prompts/get", params) } catch (missing: Rpc.NotFound) { throw SafeFailure(FailureKind.MCP) }
            result.arr("messages").filterIsInstance<JsonObject>().mapNotNull { m ->
                contentText(m["content"]).takeIf { it.isNotBlank() }?.let { McpPromptMessage(m.str("role"), it) }
            }
        }
    }

    private fun contentText(content: JsonElement?): String = when (content) {
        is JsonObject -> blockText(content)
        is JsonArray -> content.filterIsInstance<JsonObject>().joinToString("\n") { blockText(it) }.trim()
        else -> ""
    }
    private fun blockText(block: JsonObject): String = when (block.str("type")) {
        "text" -> block.str("text")
        "resource" -> block.obj("resource").str("text")
        else -> ""
    }

    private fun newRpc(server: McpServer, credentials: Credentials): Rpc {
        require(server.timeoutSeconds in 10..600)
        return Rpc(http(server), TransportPolicy.validate(server.url, server.allowLocalHttp), credentials)
    }

    private suspend fun <T> withSession(server: McpServer, credentials: Credentials, block: suspend Rpc.() -> T): T = withContext(Dispatchers.IO) {
        val rpc = newRpc(server, credentials)
        try {
            rpc.guarded {
                rpc.initialize()
                // HTTP 404 on an established session means the server terminated it: start a new one exactly once.
                try { block(rpc) } catch (expired: Rpc.Expired) { rpc.initialize(); block(rpc) }
            }
        } finally {
            withContext(NonCancellable) { withTimeoutOrNull(TERMINATE_TIMEOUT_MS) { runCatching { rpc.terminate() } } }
        }
    }

    /** Opens a retained session for the tool-calling loop; the caller MUST close it in a finally block. */
    suspend fun openSession(server: McpServer, credentials: Credentials): McpToolSession = withContext(Dispatchers.IO) {
        val rpc = newRpc(server, credentials)
        rpc.guarded { rpc.initialize() }
        McpToolSession(rpc)
    }

    internal class Rpc(private val client: OkHttpClient, private val url: HttpUrl, private val credentials: Credentials) {
        class Expired : Exception()
        class NotFound : Exception()
        var session: McpSession? = null; private set
        private var sessionId: String? = null
        private var protocol = MCP_PROTOCOL_LATEST
        private var nextId = 0
        private val currentCall = AtomicReference<Call?>(null)

        fun cancelCurrent() { currentCall.get()?.cancel() }

        // Coroutine cancellation must interrupt an in-flight blocking HTTP read immediately:
        // a suspended watchdog child observes cancellation and cancels the current call.
        suspend fun <T> guarded(block: suspend () -> T): T = coroutineScope {
            val watchdog = launch { try { awaitCancellation() } finally { cancelCurrent() } }
            try { block() } finally { watchdog.cancel() }
        }

        private fun builder(): Request.Builder = Request.Builder().url(url).apply {
            credentials.headers.forEach { (k, v) ->
                require(k.lowercase() !in BLOCKED_HEADERS)
                header(k, v)
            }
            if (credentials.key.isNotBlank() && credentials.headers.keys.none { it.equals("Authorization", true) }) {
                header("Authorization", "Bearer ${credentials.key}")
            }
            header("Accept", "application/json, text/event-stream")
            sessionId?.let { header("Mcp-Session-Id", it) }
            if (session != null) header("MCP-Protocol-Version", protocol)
        }

        private suspend fun post(message: JsonObject): Response {
            val call = client.newCall(builder().post(message.toString().toRequestBody("application/json".toMediaType())).build())
            currentCall.set(call)
            return call.awaitResponse()
        }

        suspend fun initialize() {
            session = null; sessionId = null; protocol = MCP_PROTOCOL_LATEST
            val id = ++nextId
            val message = buildJsonObject {
                put("jsonrpc", "2.0"); put("id", id); put("method", "initialize")
                putJsonObject("params") {
                    put("protocolVersion", MCP_PROTOCOL_LATEST)
                    putJsonObject("capabilities") { }
                    putJsonObject("clientInfo") { put("name", MCP_CLIENT_NAME); put("version", MCP_CLIENT_VERSION) }
                }
            }
            post(message).use { response ->
                if (!response.isSuccessful) throw mcpHttpFailure(response.code)
                sessionId = response.header("Mcp-Session-Id")?.takeIf { it.length <= MAX_SESSION_ID && it.all { c -> c in '!'..'~' } }
                val result = resultOf(id, response)
                val version = result.str("protocolVersion")
                if (version !in MCP_PROTOCOL_VERSIONS) throw SafeFailure(FailureKind.MCP)
                protocol = version
                val caps = result.obj("capabilities")
                val info = result.obj("serverInfo")
                session = McpSession(
                    McpServerInfo(info.str("name"), info.str("title"), info.str("version")), protocol,
                    McpCapabilities(caps.containsKey("tools"), caps.containsKey("resources"), caps.containsKey("prompts")),
                    result.str("instructions"),
                )
            }
            post(buildJsonObject { put("jsonrpc", "2.0"); put("method", "notifications/initialized") }).use { response ->
                if (!response.isSuccessful) throw mcpHttpFailure(response.code)
            }
        }

        suspend fun request(method: String, params: JsonObject?): JsonObject {
            val id = ++nextId
            val message = buildJsonObject {
                put("jsonrpc", "2.0"); put("id", id); put("method", method)
                if (params != null) put("params", params)
            }
            post(message).use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 && sessionId != null) throw Expired()
                    throw mcpHttpFailure(response.code)
                }
                return resultOf(id, response)
            }
        }

        private suspend fun resultOf(id: Int, response: Response): JsonObject {
            val body = response.body ?: throw SafeFailure(FailureKind.MALFORMED)
            if (response.header("Content-Type").orEmpty().contains("text/event-stream", true)) {
                val reader = SseReader(body.charStream(), 2 * 1024 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val event = reader.next() ?: break
                    val message = json.parseToJsonElement(event.data()) as? JsonObject ?: continue
                    // Notifications and server-initiated requests in the stream are ignored on stage 1.
                    if ((message.containsKey("result") || message.containsKey("error")) &&
                        (message["id"] as? JsonPrimitive)?.content == id.toString()
                    ) return checked(message)
                }
                throw SafeFailure(FailureKind.TRUNCATED)
            }
            val text = body.source().readUtf8Limited(4L * 1024 * 1024)
            return checked(json.parseToJsonElement(text) as? JsonObject ?: throw SafeFailure(FailureKind.MALFORMED))
        }

        private fun checked(message: JsonObject): JsonObject {
            val error = message.obj("error")
            if (error.isNotEmpty()) {
                if (error.num("code") == -32601L) throw NotFound()
                throw SafeFailure(FailureKind.MCP)
            }
            return message.obj("result")
        }

        suspend fun <T> list(method: String, key: String, parse: (JsonObject) -> T?): List<T> {
            val out = mutableListOf<T>()
            var cursor = ""; var page = 0
            do {
                val params = if (cursor.isEmpty()) null else buildJsonObject { put("cursor", cursor) }
                // -32601 means the server does not implement the list despite advertising the capability.
                val result = try { request(method, params) } catch (missing: NotFound) { return out }
                result.arr(key).filterIsInstance<JsonObject>().forEach { o -> parse(o)?.let(out::add) }
                cursor = result.str("nextCursor")
                page++
            } while (cursor.isNotEmpty() && page < MAX_PAGES)
            return out
        }

        suspend fun terminate() {
            if (sessionId == null) return
            val call = client.newCall(builder().delete().build())
            currentCall.set(call)
            call.awaitResponse().use { }
        }
    }

    companion object { const val MAX_RESOURCE_CHARS = 1_000_000 }
}

/** Retained MCP session for the tool-calling loop; every operation keeps the watchdog and single 404-retry semantics. */
class McpToolSession internal constructor(private val rpc: McpClient.Rpc) {
    suspend fun tools(): List<McpTool> = rpc.guarded {
        val session = rpc.session ?: throw SafeFailure(FailureKind.MCP)
        if (!session.capabilities.tools) return@guarded emptyList()
        try { rpc.list("tools/list", "tools", ::parseMcpTool) }
        catch (expired: McpClient.Rpc.Expired) { rpc.initialize(); rpc.list("tools/list", "tools", ::parseMcpTool) }
    }
    suspend fun callTool(name: String, arguments: JsonObject?): McpToolResult = rpc.guarded {
        require(name.isNotBlank())
        val params = buildJsonObject {
            put("name", name)
            put("arguments", arguments ?: JsonObject(emptyMap()))
        }
        val result = try {
            try { rpc.request("tools/call", params) }
            catch (expired: McpClient.Rpc.Expired) { rpc.initialize(); rpc.request("tools/call", params) }
        } catch (missing: McpClient.Rpc.NotFound) { throw SafeFailure(FailureKind.MCP) }
        val text = toolResultText(result)
        val capped = if (text.length > McpClient.MAX_RESOURCE_CHARS) text.take(McpClient.MAX_RESOURCE_CHARS) + "\n…[обрезано]" else text
        McpToolResult(capped, result["isError"] == JsonPrimitive(true))
    }
    suspend fun close() {
        withContext(NonCancellable) { withTimeoutOrNull(TERMINATE_TIMEOUT_MS) { runCatching { rpc.terminate() } } }
    }
}

internal fun parseMcpTool(o: JsonObject): McpTool? =
    o.str("name").takeIf(String::isNotBlank)?.let { McpTool(it, o.str("title"), o.str("description"), o.obj("inputSchema")) }

/** Renders tools/call result content blocks; non-text blocks become safe markers (spec 2025-06-18). */
internal fun toolResultText(result: JsonObject): String {
    val blocks = result.arr("content").filterIsInstance<JsonObject>()
    val text = blocks.joinToString("\n") { b ->
        when (b.str("type")) {
            "text" -> b.str("text")
            "resource" -> b.obj("resource").str("text").ifEmpty { "[resource ${b.obj("resource").str("uri")}]" }
            else -> "[${b.str("type").ifEmpty { "content" }}]"
        }
    }.trim()
    return text.ifEmpty { result.obj("structuredContent").takeIf { it.isNotEmpty() }?.toString().orEmpty() }
}

private fun mcpHttpFailure(code: Int) = SafeFailure(when (code) {
    401, 403 -> FailureKind.AUTH
    408, 504 -> FailureKind.TIMEOUT
    429 -> FailureKind.RATE_LIMIT
    in 400..499 -> FailureKind.MCP
    else -> FailureKind.SERVER
}, code)
