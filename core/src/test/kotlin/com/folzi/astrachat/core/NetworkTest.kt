package com.folzi.astrachat.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class NetworkTest {
    private fun request(server: MockWebServer) = ChatRequest(
        Provider("local", "Local", server.url("/v1").toString(), allowLocalHttp = true), Model("test", "local"),
        Credentials(key = "test-credential-not-a-real-key"), listOf(Turn("user", "hello")), Generation(),
    )
    @Test fun streamsRealHttpFramesAndUsage() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":1}}\n\ndata: [DONE]\n\n"))
            val events = ProviderClient().generate(request(server)).toList()
            assertEquals("hi", events.joinToString("") { it.text }); assertTrue(events.last().terminal)
            assertEquals(5L, events[1].usage.input); assertEquals("/v1/chat/completions", server.takeRequest().path)
        }
    }
    @Test fun missingTerminalIsNotSuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"))
            val text = StringBuilder()
            try { ProviderClient().generate(request(server)).collect { text.append(it.text) }; fail("Expected truncated stream") }
            catch (e: SafeFailure) { assertEquals(FailureKind.TRUNCATED, e.kind) }
            assertEquals("partial", text.toString())
        }
    }
    @Test fun errorsDoNotEchoCredentialsOrBodies() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("secret-content Authorization: Bearer test-credential-not-a-real-key"))
            try { ProviderClient().generate(request(server)).toList(); fail("Expected auth failure") }
            catch (e: SafeFailure) { assertEquals(FailureKind.AUTH, e.kind); assertFalse(e.toString().contains("secret-content")); assertEquals("category=AUTH; HTTP 401", e.technical) }
        }
    }
    @Test fun redirectCannotForwardCredentials() = runBlocking {
        MockWebServer().use { first -> MockWebServer().use { second ->
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", second.url("/stolen")))
            try { ProviderClient().generate(request(first)).toList(); fail("Redirect must not execute") } catch (_: SafeFailure) { }
            assertEquals(0, second.requestCount)
        } }
    }
    @Test fun cancellationDoesNotWaitForReadTimeout() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: {\"choices\":[]}\n\n").throttleBody(1, 1, java.util.concurrent.TimeUnit.SECONDS))
            val job = launch { ProviderClient().generate(request(server)).collect() }
            delay(100)
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }
    @Test fun httpRequiresLocalConsentAndNoEmbeddedSecrets() {
        listOf("http://example.com/v1", "http://192.168.1.2/v1").forEach { url ->
            try { TransportPolicy.validate(url, false); fail("HTTP allowed without consent") } catch (_: SafeFailure) { }
        }
        assertEquals("192.168.1.2", TransportPolicy.validate("http://192.168.1.2/v1", true).host)
        listOf("https://user:password@example.com", "https://example.com?key=secret", "http://example.com").forEach { url ->
            try { TransportPolicy.validate(url, true); fail("Unsafe URL accepted") } catch (_: SafeFailure) { }
        }
    }
    @Test fun contextAndSslErrorsAreSpecific() {
        assertEquals(FailureKind.CONTEXT, httpFailure(400, """{"error":{"code":"context_length_exceeded"}}""").kind)
        assertEquals(FailureKind.SSL, safeFailure(javax.net.ssl.SSLException("do not echo")).kind)
        assertEquals(FailureKind.TIMEOUT, safeFailure(java.net.SocketTimeoutException()).kind)
        assertEquals(FailureKind.MODEL, httpFailure(404, "").kind)
        assertEquals(FailureKind.RATE_LIMIT, httpFailure(429, "").kind)
    }
}
