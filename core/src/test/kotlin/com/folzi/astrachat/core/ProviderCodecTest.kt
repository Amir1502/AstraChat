package com.folzi.astrachat.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ProviderCodecTest {
    private fun request(protocol: Protocol, parameters: Set<String> = emptySet()) = ChatRequest(
        Provider("test", "Test", "https://example.com/v1", protocol), Model("model", "test", parameters = parameters), Credentials(),
        listOf(Turn("user", "Hello")), Generation(system = "Be kind", temperature = 0.5, topP = 0.8, maxOutput = 20),
    )
    @Test fun chatCompletionsHasMessagesAndUsage() {
        val o = ProviderCodec.body(request(Protocol.CHAT_COMPLETIONS, setOf("temperature")), true)
        assertEquals("system", o.arr("messages")[0].jsonObject.str("role"))
        assertEquals(0.5, o["temperature"]!!.jsonPrimitive.double, 0.0)
        assertFalse(o.containsKey("top_p")); assertEquals(JsonPrimitive(true), o.obj("stream_options")["include_usage"])
    }
    @Test fun responsesUsesInputNotMessages() {
        val o = ProviderCodec.body(request(Protocol.RESPONSES), true)
        assertTrue(o.containsKey("input")); assertFalse(o.containsKey("messages")); assertEquals(20L, o.num("max_output_tokens"))
        assertEquals(JsonPrimitive(false), o["store"])
    }
    @Test fun anthropicHasSeparateSystem() {
        val o = ProviderCodec.body(request(Protocol.ANTHROPIC), false)
        assertEquals("Be kind", o.str("system")); assertEquals(1, o.arr("messages").size)
        assertFalse(o.containsKey("temperature")); assertFalse(o.containsKey("stream_options"))
    }
    @Test fun geminiHasPartsAndGenerationConfig() {
        val o = ProviderCodec.body(request(Protocol.GEMINI), true)
        assertTrue(o.containsKey("systemInstruction")); assertEquals(20L, o.obj("generationConfig").num("maxOutputTokens"))
        assertFalse(o.containsKey("stream")); assertEquals("user", o.arr("contents")[0].jsonObject.str("role"))
    }
    @Test fun decodesAllStreamingProtocols() {
        assertEquals("hi", ProviderCodec.decode(Protocol.CHAT_COMPLETIONS, "", """{"choices":[{"delta":{"content":"hi"}}]}""").text)
        assertEquals("hi", ProviderCodec.decode(Protocol.RESPONSES, "", """{"type":"response.output_text.delta","delta":"hi"}""").text)
        assertEquals("hi", ProviderCodec.decode(Protocol.ANTHROPIC, "", """{"type":"content_block_delta","delta":{"type":"text_delta","text":"hi"}}""").text)
        assertEquals("hi", ProviderCodec.decode(Protocol.GEMINI, "", """{"candidates":[{"content":{"parts":[{"text":"hi"}]},"finishReason":"STOP"}]}""").text)
    }
    @Test fun partialAnthropicUsageMergesCache() {
        val start = ProviderCodec.decode(Protocol.ANTHROPIC, "", """{"type":"message_start","message":{"usage":{"input_tokens":5,"cache_read_input_tokens":10,"output_tokens":0}}}""")
        val delta = ProviderCodec.decode(Protocol.ANTHROPIC, "", """{"type":"message_delta","usage":{"output_tokens":7}}""")
        val result = start.usage.merge(delta.usage)
        assertEquals(15L, result.input); assertEquals(7L, result.output); assertEquals(10L, result.cached)
    }
    @Test fun usageReplacesEstimateOnlyOnCompletion() {
        val turns = listOf(Turn("user", "hello"))
        assertTrue(usageTotals(Usage(), turns, "hello", true).estimated)
        val exact = usageTotals(Usage(120, 30, reasoning = 5, cached = 2, total = 150), turns, "hello", true)
        assertFalse(exact.estimated); assertEquals(150L, exact.total); assertEquals(30L, exact.output)
        val partial = usageTotals(Usage(120, 0), turns, "12345678", false)
        assertTrue(partial.estimated); assertEquals(2L, partial.output)
    }
    @Test fun geminiReasoningIncludedOnce() {
        val c = ProviderCodec.decode(Protocol.GEMINI, "", """{"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"thoughtsTokenCount":3,"totalTokenCount":18}}""")
        assertEquals(8L, c.usage.output); assertEquals(18L, c.usage.total)
    }
    @Test(expected = IllegalArgumentException::class) fun extraCannotReplaceHistory() {
        val r = request(Protocol.RESPONSES)
        ProviderCodec.body(r.copy(generation = r.generation.copy(extraJson = """{"input":[]}""")), false)
    }
    @Test fun nonStreamingResponsesText() {
        val c = ProviderCodec.decode(Protocol.RESPONSES, "", """{"status":"completed","output":[{"content":[{"type":"output_text","text":"hello"}]}],"usage":{"input_tokens":4,"output_tokens":1}}""", false)
        assertEquals("hello", c.text); assertTrue(c.terminal); assertEquals(4L, c.usage.input)
    }
    @Test fun sseAndMetricsProductionAssertions() { OfflineChecks.main(emptyArray()) }

    @Test fun chatCompletionsCarriesToolsAndToolTurns() {
        val tool = McpTool("get_weather", "Weather", "Weather by city", buildJsonObject { put("type", "object") })
        val r = request(Protocol.CHAT_COMPLETIONS, setOf("temperature")).copy(
            turns = listOf(
                Turn("user", "Погода?"),
                Turn("assistant", "", listOf(ToolCall("call_1", "get_weather", """{"city":"M"}"""))),
                Turn("tool", "Sunny", toolCallId = "call_1"),
            ),
            tools = listOf(tool),
        )
        val o = ProviderCodec.body(r, true)
        val exposed = o.arr("tools")[0].jsonObject
        assertEquals("function", exposed.str("type"))
        assertEquals("get_weather", exposed.obj("function").str("name"))
        assertEquals("Weather by city", exposed.obj("function").str("description"))
        assertEquals("object", exposed.obj("function").obj("parameters").str("type"))
        val messages = o.arr("messages").filterIsInstance<JsonObject>()
        val assistant = messages[2]
        assertFalse(assistant.containsKey("content"))
        val call = assistant.arr("tool_calls")[0].jsonObject
        assertEquals("call_1", call.str("id")); assertEquals("function", call.str("type"))
        assertEquals("get_weather", call.obj("function").str("name"))
        assertEquals("""{"city":"M"}""", call.obj("function").str("arguments"))
        val toolMessage = messages[3]
        assertEquals("tool", toolMessage.str("role"))
        assertEquals("call_1", toolMessage.str("tool_call_id"))
        assertEquals("Sunny", toolMessage.str("content"))
    }

    @Test(expected = IllegalArgumentException::class) fun toolsRejectedOutsideChatCompletions() {
        ProviderCodec.body(request(Protocol.ANTHROPIC).copy(tools = listOf(McpTool("t"))), false)
    }

    @Test fun streamingToolCallFragmentsDecoded() {
        val c = ProviderCodec.decode(Protocol.CHAT_COMPLETIONS, "", """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"f","arguments":""}},{"function":{"arguments":"{\"x\""}}]}}]}""")
        assertEquals(listOf(ToolCallFragment(0, "c1", "f", ""), ToolCallFragment(0, "", "", "{\"x\"")), c.toolFragments)
        assertTrue(c.toolCalls.isEmpty()); assertFalse(c.terminal); assertEquals("", c.text)
    }

    @Test fun nonStreamingToolCallsDecoded() {
        val c = ProviderCodec.decode(Protocol.CHAT_COMPLETIONS, "", """{"choices":[{"message":{"role":"assistant","tool_calls":[{"id":"c1","type":"function","function":{"name":"f","arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""", false)
        assertEquals(listOf(ToolCall("c1", "f", "{}")), c.toolCalls)
        assertTrue(c.terminal); assertTrue(c.toolFragments.isEmpty())
    }
}
