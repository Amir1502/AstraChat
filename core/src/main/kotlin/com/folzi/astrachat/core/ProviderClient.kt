package com.folzi.astrachat.core

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

interface ChatGateway {
    fun generate(request: ChatRequest): Flow<Chunk>
    suspend fun models(provider: Provider, credentials: Credentials): List<String>
}
class ProviderClient(private val base: OkHttpClient = OkHttpClient()) : ChatGateway {
    private fun client(p: Provider) = base.newBuilder()
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(p.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .callTimeout(p.timeoutSeconds.toLong(), TimeUnit.SECONDS).build()

    private fun authenticatedBuilder(p: Provider, credentials: Credentials, url: HttpUrl): Request.Builder {
        val query = url.newBuilder().apply { credentials.query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return Request.Builder().url(query).apply {
            credentials.headers.forEach { (k, v) ->
                require(k.lowercase() !in setOf("host", "content-length", "connection", "transfer-encoding", "accept", "content-type"))
                header(k, v)
            }
            if (credentials.key.isNotBlank()) when (p.protocol) {
                Protocol.ANTHROPIC -> header("x-api-key", credentials.key)
                Protocol.GEMINI -> header("x-goog-api-key", credentials.key)
                else -> header("Authorization", "Bearer ${credentials.key}")
            }
            if (p.protocol == Protocol.ANTHROPIC) header("anthropic-version", "2023-06-01")
        }
    }
    override fun generate(request: ChatRequest): Flow<Chunk> = callbackFlow {
        val callRef = AtomicReference<Call?>()
        val job = launch(Dispatchers.IO) {
            try {
                val p = request.provider
                require(p.timeoutSeconds in 10..600)
                val url = TransportPolicy.endpoint(p, request.model, p.streaming).newBuilder().apply {
                    if (p.streaming && p.protocol == Protocol.GEMINI) addQueryParameter("alt", "sse")
                }.build()
                val call = client(p).newCall(authenticatedBuilder(p, request.credentials, url)
                    .header("Accept", if (p.streaming) "text/event-stream" else "application/json")
                    .post(ProviderCodec.body(request, p.streaming).toString().toRequestBody("application/json".toMediaType())).build())
                callRef.set(call)
                ensureActive()
                call.execute().use { response ->
                    if (!response.isSuccessful) throw httpFailure(response.code, response.peekBody(16384).string())
                    val body = response.body ?: throw SafeFailure(FailureKind.MALFORMED)
                    if (!p.streaming) {
                        val content = body.source().readUtf8Limited(4L * 1024 * 1024)
                        val chunk = ProviderCodec.decode(p.protocol, "", content, false)
                        if (chunk.text.isEmpty()) throw SafeFailure(FailureKind.MALFORMED)
                        send(chunk)
                    } else {
                        if (!response.header("Content-Type").orEmpty().contains("text/event-stream", true)) throw SafeFailure(FailureKind.MALFORMED)
                        val reader = SseReader(body.charStream(), 2 * 1024 * 1024)
                        var ended = false
                        while (true) {
                            ensureActive()
                            val event = reader.next() ?: break
                            val chunk = ProviderCodec.decode(p.protocol, event.event(), event.data())
                            send(chunk)
                            ended = ended || chunk.terminal
                            // Gemini may include final usage in a subsequent frame; drain to EOF.
                            if (chunk.terminal && p.protocol != Protocol.GEMINI) break
                        }
                        if (!ended) throw SafeFailure(FailureKind.TRUNCATED)
                    }
                }
                close()
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { close(safeFailure(failure)) }
        }
        awaitClose { callRef.get()?.cancel(); job.cancel() }
    }
    override suspend fun models(provider: Provider, credentials: Credentials): List<String> = withContext(Dispatchers.IO) {
        val base = TransportPolicy.validate(provider.baseUrl, provider.allowLocalHttp)
        val path = provider.modelsEndpoint.ifBlank { "models" }
        require(!path.contains("://") && !path.contains("..") && !path.contains('?') && !path.startsWith("//"))
        val result = mutableListOf<String>()
        var page = ""
        var pages = 0
        do {
            val url = base.newBuilder().addPathSegments(path.trimStart('/')).apply {
                if (page.isNotEmpty()) addQueryParameter("pageToken", page)
            }.build()
            var attempt = 0
            var response: Response
            while (true) {
                ensureActive()
                val call = client(provider).newCall(authenticatedBuilder(provider, credentials, url).get().build())
                response = call.awaitResponse()
                if (response.code !in setOf(429, 502, 503, 504) || attempt >= 2) break
                val wait = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 30)?.times(1000) ?: (500L shl attempt)
                response.close(); delay(wait); attempt++
            }
            response.use {
                if (!it.isSuccessful) throw httpFailure(it.code, it.peekBody(16384).string())
                val text = it.body?.source()?.readUtf8Limited(2L * 1024 * 1024) ?: throw SafeFailure(FailureKind.MALFORMED)
                val o = json.parseToJsonElement(text).jsonObject
                val entries = o.arr(if (provider.protocol == Protocol.GEMINI) "models" else "data")
                result += entries.filterIsInstance<JsonObject>().map { m -> m.str(if (provider.protocol == Protocol.GEMINI) "name" else "id").removePrefix("models/") }.filter(String::isNotBlank)
                page = if (provider.protocol == Protocol.GEMINI) o.str("nextPageToken") else ""
            }
            pages++
        } while (page.isNotBlank() && pages < 20)
        result.distinct().sorted()
    }
}
internal fun okio.BufferedSource.readUtf8Limited(limit: Long): String {
    request(limit + 1)
    if (buffer.size > limit) throw SafeFailure(FailureKind.MALFORMED)
    return readUtf8()
}
internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) { if (!cont.isCancelled) cont.resumeWith(Result.failure(safeFailure(e))) }
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
    })
}
