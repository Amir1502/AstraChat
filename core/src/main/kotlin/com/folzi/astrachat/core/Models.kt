package com.folzi.astrachat.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
enum class Protocol { CHAT_COMPLETIONS, RESPONSES, ANTHROPIC, GEMINI }
@Serializable
data class Provider(
    val id: String, val name: String, val baseUrl: String,
    val protocol: Protocol = Protocol.CHAT_COMPLETIONS,
    val icon: String = "✦", val endpoint: String = "", val modelsEndpoint: String = "",
    val timeoutSeconds: Int = 120, val streaming: Boolean = true,
    val allowLocalHttp: Boolean = false, val sendStreamUsage: Boolean = true,
)
@Serializable
data class Credentials(val key: String = "", val headers: Map<String, String> = emptyMap(), val query: Map<String, String> = emptyMap())
@Serializable
data class Model(
    val id: String, val providerId: String, val contextWindow: Int = 32768,
    val inputPrice: Double? = null, val outputPrice: Double? = null,
    val parameters: Set<String> = setOf("temperature", "top_p", "stop"),
)
@Serializable
data class Generation(
    val system: String = "You are a helpful assistant.",
    val temperature: Double? = null, val topP: Double? = null, val maxOutput: Int = 2048,
    val stops: List<String> = emptyList(), val reasoning: String = "", val extraJson: String = "{}",
)
@Serializable
data class Turn(val role: String, val text: String)
data class ChatRequest(val provider: Provider, val model: Model, val credentials: Credentials, val turns: List<Turn>, val generation: Generation)
data class Usage(val input: Long? = null, val output: Long? = null, val reasoning: Long? = null, val cached: Long? = null, val total: Long? = null) {
    fun merge(next: Usage) = Usage(next.input ?: input, next.output ?: output, next.reasoning ?: reasoning, next.cached ?: cached, next.total ?: total)
}
data class Chunk(val text: String = "", val usage: Usage = Usage(), val terminal: Boolean = false)
data class Totals(val input: Long, val output: Long, val total: Long, val reasoning: Long?, val cached: Long?, val estimated: Boolean)

fun usageTotals(usage: Usage, turns: List<Turn>, text: String, complete: Boolean): Totals {
    val input = usage.input ?: (turns.sumOf { TokenMath.estimate(it.text) + 4 } + 2)
    val output = if (complete) usage.output ?: TokenMath.estimate(text) else TokenMath.estimate(text)
    return Totals(input, output, if (complete) usage.total ?: (input + output) else input + output,
        usage.reasoning, usage.cached, !complete || usage.input == null || usage.output == null)
}
object Profiles {
    val defaults = listOf(
        Provider("openai", "OpenAI", "https://api.openai.com/v1"),
        Provider("anthropic", "Anthropic Claude", "https://api.anthropic.com/v1", Protocol.ANTHROPIC),
        Provider("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", Protocol.GEMINI),
        Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1"),
        Provider("mistral", "Mistral", "https://api.mistral.ai/v1", sendStreamUsage = false),
        Provider("xai", "xAI", "https://api.x.ai/v1"),
        Provider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
        Provider("groq", "Groq", "https://api.groq.com/openai/v1", sendStreamUsage = false),
        Provider("ollama", "Ollama", "http://127.0.0.1:11434/v1", sendStreamUsage = false),
    )
}
fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())
fun JsonObject.arr(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
fun JsonObject.num(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
