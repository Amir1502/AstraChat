package com.folzi.astrachat.core

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val TERMINATE_TIMEOUT_MS = 2000L
private const val MAX_SESSION_ID = 512
private val BLOCKED_HEADERS = setOf("host", "content-length", "connection", "transfer-encoding", "accept", "content-type", "mcp-session-id", "mcp-protocol-version")

/** MCP client for the Streamable HTTP transport (protocol revision 2025-06-18).
 * Sessions are ephemeral: every public call runs initialize -> operation -> DELETE. */
class McpClient(private val base: OkHttpClient = OkHttpClient()) {
    private fun http(server: McpServer) = base.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(server.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .callTimeout(server.timeoutSeconds.toLong(), TimeUnit.SECONDS).build()

    suspend fun discover(server: McpServer, credentials: Credentials): McpDiscovery = withSession(server, credentials) {
        val s = session ?: throw SafeFailure(FailureKind.MCP)
        McpDiscovery(server.id, s,
            if (s.capabilities.tools) list("tools/list", "tools") { o -> o.str("name").takeIf(String::isNotBlank)?.let { McpTool(it, o.str("title"), o.str("description"), o.obj("inputSchema")) } } else emptyList(),
            if (s.capabilities.resources) list("resources/list", "resources") { o -> o.str("uri").takeIf(String::isNotBlank)?.let { McpResource(it, o.str("name"), o.str("description"), o.str("mimeType")) } } else emptyList(),
            if (s.capabilities.prompts) list("prompts/list", "prompts") { o -> o.str("name").takeIf(String::isNotBlank)?.let { McpPrompt(it, o.str("title"), o.str("description"), o.arr("arguments").filterIsInstance<JsonObject>().map { a -> McpPromptArgument(a.str("name"), a.str("description"), a["required"] == JsonPrimitive(true)) }.filter { a -> a.name.isNotBlank() }) } } else emptyList())
    }

    private suspend fun <T> withSession(server: McpServer, credentials: Credentials, block: suspend Rpc.() -> T): T = withContext(Dispatchers.IO) {
        require(server.timeoutSeconds in 10..600)
        val rpc = Rpc(http(server), TransportPolicy.validate(server.url, server.allowLocalHttp), credentials)
        try {
            rpc.initialize()
            block(rpc)
        } finally {
            withContext(NonCancellable) { withTimeoutOrNull(TERMINATE_TIMEOUT_MS) { runCatching { rpc.terminate() } } }
        }
    }

    private class Rpc(private val client: OkHttpClient, private val url: HttpUrl, private val credentials: Credentials) {
        var session: McpSession? = null; private set
        private var sessionId: String? = null
        private var protocol = MCP_PROTOCOL_LATEST
        private var nextId = 0

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

        private suspend fun post(message: JsonObject): Response =
            client.newCall(builder().post(message.toString().toRequestBody("application/json".toMediaType())).build()).awaitResponse()

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
                val result = resultOf(response)
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
                if (!response.isSuccessful) throw mcpHttpFailure(response.code)
                return resultOf(response)
            }
        }

        private suspend fun resultOf(response: Response): JsonObject {
            val body = response.body ?: throw SafeFailure(FailureKind.MALFORMED)
            val text = body.source().readUtf8Limited(4L * 1024 * 1024)
            return checked(json.parseToJsonElement(text) as? JsonObject ?: throw SafeFailure(FailureKind.MALFORMED))
        }

        private fun checked(message: JsonObject): JsonObject {
            if (message.obj("error").isNotEmpty()) throw SafeFailure(FailureKind.MCP)
            return message.obj("result")
        }

        suspend fun <T> list(method: String, key: String, parse: (JsonObject) -> T?): List<T> =
            request(method, null).arr(key).filterIsInstance<JsonObject>().mapNotNull(parse)

        suspend fun terminate() {
            if (sessionId == null) return
            client.newCall(builder().delete().build()).awaitResponse().use { }
        }
    }
}

private fun mcpHttpFailure(code: Int) = SafeFailure(when (code) {
    401, 403 -> FailureKind.AUTH
    408, 504 -> FailureKind.TIMEOUT
    429 -> FailureKind.RATE_LIMIT
    in 400..499 -> FailureKind.MCP
    else -> FailureKind.SERVER
}, code)
