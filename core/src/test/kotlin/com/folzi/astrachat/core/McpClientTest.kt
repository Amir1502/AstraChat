package com.folzi.astrachat.core

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class McpClientTest {
    private fun server(mock: MockWebServer) = McpServer("test-server", "Test", mock.url("/mcp").toString(), allowLocalHttp = true)
    private val credentials = Credentials(key = "test-credential-not-a-real-key")
    private fun initResponse(caps: String = "{}", version: String = "2025-06-18", sessionId: String? = "test-session") =
        MockResponse().setHeader("Content-Type", "application/json")
            .apply { sessionId?.let { setHeader("Mcp-Session-Id", it) } }
            .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"$version","capabilities":$caps,"serverInfo":{"name":"test-server","version":"0.1"},"instructions":"be helpful"}}""")
    private fun accepted() = MockResponse().setResponseCode(202)
    private fun ok() = MockResponse().setResponseCode(200)

    @Test fun promptInsertPrefersUserMessages() {
        assertEquals("hi", promptInsertText(listOf(McpPromptMessage("assistant", "sys"), McpPromptMessage("user", "hi"))))
        assertEquals("a\n\nb", promptInsertText(listOf(McpPromptMessage("user", "a"), McpPromptMessage("assistant", "x"), McpPromptMessage("user", "b"))))
        assertEquals("only assistant", promptInsertText(listOf(McpPromptMessage("assistant", "only assistant"))))
        assertEquals("", promptInsertText(emptyList()))
    }

    @Test fun initializeNegotiatesSessionHeadersAndTerminates() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse()); mock.enqueue(accepted()); mock.enqueue(ok())
            val d = McpClient().discover(server(mock), credentials)
            assertEquals("test-server", d.session.info.name); assertEquals("0.1", d.session.info.version)
            assertEquals("2025-06-18", d.session.protocolVersion); assertEquals("be helpful", d.session.instructions)
            assertFalse(d.session.capabilities.tools || d.session.capabilities.resources || d.session.capabilities.prompts)
            assertTrue(d.tools.isEmpty() && d.resources.isEmpty() && d.prompts.isEmpty())
            val init = mock.takeRequest(); val initBody = init.body.readUtf8()
            assertEquals("POST", init.method); assertEquals("/mcp", init.path)
            assertEquals("application/json, text/event-stream", init.getHeader("Accept"))
            assertTrue(initBody.contains("\"protocolVersion\":\"2025-06-18\""))
            assertTrue(initBody.contains("\"method\":\"initialize\""))
            assertTrue(initBody.contains("\"name\":\"Astra Chat\""))
            assertNull(init.getHeader("Mcp-Session-Id")); assertNull(init.getHeader("MCP-Protocol-Version"))
            val notif = mock.takeRequest(); val notifBody = notif.body.readUtf8()
            assertEquals("POST", notif.method); assertTrue(notifBody.contains("\"method\":\"notifications/initialized\""))
            assertFalse(notifBody.contains("\"id\""))
            assertEquals("test-session", notif.getHeader("Mcp-Session-Id"))
            assertEquals("2025-06-18", notif.getHeader("MCP-Protocol-Version"))
            assertEquals("Bearer test-credential-not-a-real-key", notif.getHeader("Authorization"))
            val delete = mock.takeRequest()
            assertEquals("DELETE", delete.method); assertEquals("test-session", delete.getHeader("Mcp-Session-Id"))
            assertEquals(3, mock.requestCount)
        }
    }

    @Test fun unsupportedProtocolVersionRejected() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse(version = "1999-01-01")); mock.enqueue(ok())
            try { McpClient().discover(server(mock), credentials); fail("Unsupported version accepted") }
            catch (e: SafeFailure) { assertEquals(FailureKind.MCP, e.kind) }
            assertEquals(2, mock.requestCount) // initialize + terminate, без notifications/initialized
        }
    }

    @Test fun publicHttpRejectedBeforeAnyCall() = runBlocking {
        MockWebServer().use { mock ->
            val insecure = McpServer("s", "S", mock.url("/mcp").toString(), allowLocalHttp = false)
            try { McpClient().discover(insecure, credentials); fail("Public HTTP allowed") }
            catch (e: SafeFailure) { assertEquals(FailureKind.INSECURE, e.kind) }
            assertEquals(0, mock.requestCount)
        }
    }

    @Test fun credentialsKeyAndHeadersPolicy() = runBlocking {
        MockWebServer().use { mock ->
            repeat(2) { mock.enqueue(initResponse()); mock.enqueue(accepted()); mock.enqueue(ok()) }
            McpClient().discover(server(mock), Credentials(key = "test-credential-not-a-real-key", headers = mapOf("x-api-key" to "test-header-value")))
            val first = mock.takeRequest()
            assertEquals("Bearer test-credential-not-a-real-key", first.getHeader("Authorization"))
            assertEquals("test-header-value", first.getHeader("x-api-key"))
            mock.takeRequest(); mock.takeRequest()
            McpClient().discover(server(mock), Credentials(key = "test-credential-not-a-real-key", headers = mapOf("Authorization" to "CustomScheme abc")))
            val second = mock.takeRequest()
            assertEquals("CustomScheme abc", second.getHeader("Authorization"))
        }
    }

    @Test fun malformedSessionIdHeaderIgnored() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse(sessionId = "has space")); mock.enqueue(accepted())
            val d = McpClient().discover(server(mock), credentials)
            assertEquals("test-server", d.session.info.name)
            mock.takeRequest()
            val notif = mock.takeRequest()
            assertNull(notif.getHeader("Mcp-Session-Id"))
            assertEquals("2025-06-18", notif.getHeader("MCP-Protocol-Version"))
            assertEquals(2, mock.requestCount) // terminate не отправляется без валидной сессии
        }
    }

    private fun jsonResult(id: Int, result: String) = MockResponse().setHeader("Content-Type", "application/json").setBody("""{"jsonrpc":"2.0","id":$id,"result":$result}""")
    private fun jsonError(id: Int, code: Int, message: String) = MockResponse().setHeader("Content-Type", "application/json").setBody("""{"jsonrpc":"2.0","id":$id,"error":{"code":$code,"message":"$message"}}""")
    private fun sse(vararg messages: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(messages.joinToString("") { "event: message\ndata: $it\n\n" })

    @Test fun discoveryGatedByCapabilitiesAndPaginates() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse(caps = """{"tools":{},"prompts":{}}""")); mock.enqueue(accepted())
            mock.enqueue(jsonResult(2, """{"tools":[{"name":"search","description":"find things"}],"nextCursor":"c2"}"""))
            mock.enqueue(jsonResult(3, """{"tools":[{"name":"fetch","title":"Fetch"}]}"""))
            mock.enqueue(jsonResult(4, """{"prompts":[{"name":"summarize","description":"sum it","arguments":[{"name":"topic","description":"what","required":true}]}]}"""))
            mock.enqueue(ok())
            val d = McpClient().discover(server(mock), credentials)
            assertEquals(listOf("search", "fetch"), d.tools.map { it.name })
            assertEquals("find things", d.tools[0].description)
            assertEquals("Fetch", d.tools[1].title)
            assertEquals(1, d.prompts.size); assertEquals("summarize", d.prompts[0].name)
            assertEquals(listOf(McpPromptArgument("topic", "what", true)), d.prompts[0].arguments)
            assertTrue(d.resources.isEmpty())
            assertTrue(mock.takeRequest().body.readUtf8().contains("\"method\":\"initialize\""))
            assertTrue(mock.takeRequest().body.readUtf8().contains("notifications/initialized"))
            val page1 = mock.takeRequest().body.readUtf8()
            assertTrue(page1.contains("\"method\":\"tools/list\"")); assertFalse(page1.contains("cursor"))
            val page2 = mock.takeRequest().body.readUtf8()
            assertTrue(page2.contains("\"method\":\"tools/list\"")); assertTrue(page2.contains("\"cursor\":\"c2\""))
            assertTrue(mock.takeRequest().body.readUtf8().contains("\"method\":\"prompts/list\""))
            assertEquals("DELETE", mock.takeRequest().method)
            assertEquals(6, mock.requestCount)
        }
    }

    @Test fun sseResponseDeliversResultAndIgnoresNotifications() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse(caps = """{"tools":{}}""")); mock.enqueue(accepted())
            mock.enqueue(sse(
                """{"jsonrpc":"2.0","method":"notifications/progress","params":{}}""",
                """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"search"}]}}""",
            ))
            mock.enqueue(ok())
            val d = McpClient().discover(server(mock), credentials)
            assertEquals(listOf("search"), d.tools.map { it.name })
        }
    }

    @Test fun sseStreamWithoutResponseIsTruncated() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse(caps = """{"tools":{}}""")); mock.enqueue(accepted())
            mock.enqueue(sse("""{"jsonrpc":"2.0","method":"notifications/progress","params":{}}"""))
            mock.enqueue(ok())
            try { McpClient().discover(server(mock), credentials); fail("Truncated SSE accepted") }
            catch (e: SafeFailure) { assertEquals(FailureKind.TRUNCATED, e.kind) }
        }
    }

    @Test fun jsonRpcErrorBecomesSafeFailureWithoutEcho() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse(caps = """{"tools":{}}""")); mock.enqueue(accepted())
            mock.enqueue(jsonError(2, -32603, "secret internal detail")); mock.enqueue(ok())
            try { McpClient().discover(server(mock), credentials); fail("RPC error accepted") }
            catch (e: SafeFailure) {
                assertEquals(FailureKind.MCP, e.kind)
                assertFalse(e.message.orEmpty().contains("secret")); assertEquals("category=MCP", e.technical)
            }
        }
    }

    @Test fun methodNotFoundDegradesToEmptyList() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse(caps = """{"tools":{},"prompts":{}}""")); mock.enqueue(accepted())
            mock.enqueue(jsonError(2, -32601, "Method not found"))
            mock.enqueue(jsonResult(3, """{"prompts":[{"name":"p"}]}"""))
            mock.enqueue(ok())
            val d = McpClient().discover(server(mock), credentials)
            assertTrue(d.tools.isEmpty()); assertEquals(1, d.prompts.size)
        }
    }

    @Test fun expiredSessionReinitializesOnce() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(initResponse(caps = """{"tools":{}}""", sessionId = "session-a")); mock.enqueue(accepted())
            mock.enqueue(MockResponse().setResponseCode(404))
            mock.enqueue(initResponse(caps = """{"tools":{}}""", sessionId = "session-b")); mock.enqueue(accepted())
            mock.enqueue(jsonResult(2, """{"tools":[{"name":"search"}]}""")); mock.enqueue(ok())
            val d = McpClient().discover(server(mock), credentials)
            assertEquals(listOf("search"), d.tools.map { it.name })
            val recorded = List(mock.requestCount) { mock.takeRequest() }
            val bodies = recorded.map { it.body.readUtf8() }
            assertEquals(2, bodies.count { it.contains("\"method\":\"initialize\"") })
            assertTrue(bodies[5].contains("\"method\":\"tools/list\""))
            assertEquals("session-b", recorded[5].getHeader("Mcp-Session-Id"))
        }
    }
}
