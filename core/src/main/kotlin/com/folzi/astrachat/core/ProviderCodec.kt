package com.folzi.astrachat.core

import kotlinx.serialization.json.*

object ProviderCodec {
    fun body(request: ChatRequest, stream: Boolean): JsonObject {
        val (p, m, _, turns, g) = request
        require(m.id.isNotBlank() && g.maxOutput in 1..m.contextWindow)
        require(g.temperature == null || g.temperature in 0.0..2.0)
        require(g.topP == null || g.topP in 0.0..1.0)
        val extra = json.parseToJsonElement(g.extraJson) as? JsonObject ?: throw SafeFailure(FailureKind.INVALID)
        val blocked = setOf("model", "messages", "input", "contents", "system", "systemInstruction", "instructions", "stream", "stream_options", "generationConfig", "tools", "api_key", "authorization")
        require(extra.keys.none { it in blocked })
        val system = g.system
        val supported: (String) -> Boolean = { it in m.parameters }
        val parameters = buildJsonObject {
            if (supported("temperature")) g.temperature?.let { put("temperature", it) }
            if (supported("top_p")) g.topP?.let { put(if (p.protocol == Protocol.GEMINI) "topP" else "top_p", it) }
            if (supported("stop") && g.stops.isNotEmpty()) {
                put(when (p.protocol) { Protocol.GEMINI -> "stopSequences"; Protocol.ANTHROPIC -> "stop_sequences"; else -> "stop" }, JsonArray(g.stops.map(::JsonPrimitive)))
            }
        }
        return buildJsonObject {
            when (p.protocol) {
                Protocol.CHAT_COMPLETIONS -> {
                    put("model", m.id); put("stream", stream)
                    if (stream && p.sendStreamUsage) putJsonObject("stream_options") { put("include_usage", true) }
                    putJsonArray("messages") {
                        if (system.isNotBlank()) add(buildJsonObject { put("role", "system"); put("content", system) })
                        turns.forEach { t -> add(buildJsonObject { put("role", t.role); put("content", t.text) }) }
                    }
                    parameters.forEach { (k, v) -> put(k, v) }
                    put(if ("max_completion_tokens" in m.parameters) "max_completion_tokens" else "max_tokens", g.maxOutput)
                    if (supported("reasoning_effort") && g.reasoning.isNotBlank()) put("reasoning_effort", g.reasoning)
                    extra.forEach { (k, v) -> put(k, v) }
                }
                Protocol.RESPONSES -> {
                    put("model", m.id); put("stream", stream); put("store", false)
                    put("instructions", system); put("max_output_tokens", g.maxOutput)
                    putJsonArray("input") {
                        turns.forEach { t -> add(buildJsonObject { put("role", t.role); put("content", t.text) }) }
                    }
                    parameters.filterKeys { it != "stop" }.forEach { (k, v) -> put(k, v) }
                    if (supported("reasoning_effort") && g.reasoning.isNotBlank()) putJsonObject("reasoning") { put("effort", g.reasoning) }
                    extra.forEach { (k, v) -> put(k, v) }
                }
                Protocol.ANTHROPIC -> {
                    put("model", m.id); put("stream", stream); put("system", system); put("max_tokens", g.maxOutput)
                    putJsonArray("messages") { turns.forEach { t -> add(buildJsonObject { put("role", t.role); put("content", t.text) }) } }
                    // Anthropic models may reject simultaneous temperature and top_p.
                    parameters.filterKeys { it != "top_p" || g.temperature == null }.forEach { (k, v) -> put(k, v) }
                    if (supported("reasoning_effort") && g.reasoning.isNotBlank()) putJsonObject("output_config") { put("effort", g.reasoning) }
                    extra.forEach { (k, v) -> put(k, v) }
                }
                Protocol.GEMINI -> {
                    if (system.isNotBlank()) putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", system) }) } }
                    putJsonArray("contents") {
                        turns.forEach { t -> add(buildJsonObject {
                            put("role", if (t.role == "assistant") "model" else "user")
                            putJsonArray("parts") { add(buildJsonObject { put("text", t.text) }) }
                        }) }
                    }
                    putJsonObject("generationConfig") {
                        parameters.forEach { (k, v) -> put(k, v) }; put("maxOutputTokens", g.maxOutput)
                        if (supported("reasoning_effort") && g.reasoning.isNotBlank()) putJsonObject("thinkingConfig") { put("thinkingLevel", g.reasoning) }
                        extra.forEach { (k, v) -> put(k, v) }
                    }
                }
            }
        }
    }
    fun decode(protocol: Protocol, event: String, data: String, streaming: Boolean = true): Chunk {
        if (data == "[DONE]") return Chunk(terminal = true)
        val o = json.parseToJsonElement(data) as? JsonObject ?: throw SafeFailure(FailureKind.MALFORMED)
        if (o.containsKey("error") || event == "error" || o.str("type") in setOf("error", "response.failed")) throw SafeFailure(FailureKind.SERVER)
        return when (protocol) {
            Protocol.CHAT_COMPLETIONS -> {
                val choice = o.arr("choices").firstOrNull() as? JsonObject
                val text = choice?.obj(if (streaming) "delta" else "message")?.str("content").orEmpty()
                val u = o.obj("usage")
                Chunk(text, Usage(u.num("prompt_tokens"), u.num("completion_tokens"), u.obj("completion_tokens_details").num("reasoning_tokens"), u.obj("prompt_tokens_details").num("cached_tokens"), u.num("total_tokens")), !streaming)
            }
            Protocol.RESPONSES -> {
                val type = o.str("type").ifBlank { event }
                val response = if (streaming) o.obj("response") else o
                val u = response.obj("usage")
                val text = if (type == "response.output_text.delta") o.str("delta") else if (!streaming) response.arr("output").filterIsInstance<JsonObject>().flatMap { it.arr("content") }.filterIsInstance<JsonObject>().filter { it.str("type") == "output_text" }.joinToString("") { it.str("text") } else ""
                if (type == "response.incomplete" || response.str("status") == "incomplete") throw SafeFailure(FailureKind.TRUNCATED)
                Chunk(text, Usage(u.num("input_tokens"), u.num("output_tokens"), u.obj("output_tokens_details").num("reasoning_tokens"), u.obj("input_tokens_details").num("cached_tokens"), u.num("total_tokens")), !streaming || type == "response.completed")
            }
            Protocol.ANTHROPIC -> {
                val type = o.str("type").ifBlank { event }
                val u = if (type == "message_start") o.obj("message").obj("usage") else o.obj("usage")
                val cached = u.num("cache_read_input_tokens")
                val input = u.num("input_tokens")?.let { it + (cached ?: 0) + (u.num("cache_creation_input_tokens") ?: 0) }
                val text = if (streaming) o.obj("delta").let { if (it.str("type") == "text_delta") it.str("text") else "" } else o.arr("content").filterIsInstance<JsonObject>().filter { it.str("type") == "text" }.joinToString("") { it.str("text") }
                Chunk(text, Usage(input, u.num("output_tokens"), cached = cached), !streaming || type == "message_stop")
            }
            Protocol.GEMINI -> {
                val candidate = o.arr("candidates").firstOrNull() as? JsonObject
                val text = candidate?.obj("content")?.arr("parts")?.filterIsInstance<JsonObject>()?.filter { it["thought"] != JsonPrimitive(true) }?.joinToString("") { it.str("text") }.orEmpty()
                val u = o.obj("usageMetadata")
                val reason = u.num("thoughtsTokenCount")
                val output = u.num("candidatesTokenCount")?.let { it + (reason ?: 0) }
                Chunk(text, Usage(u.num("promptTokenCount"), output, reason, u.num("cachedContentTokenCount"), u.num("totalTokenCount")), !streaming || !candidate?.str("finishReason").isNullOrBlank())
            }
        }
    }
}
