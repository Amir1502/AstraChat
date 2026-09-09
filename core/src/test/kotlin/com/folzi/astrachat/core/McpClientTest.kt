package com.folzi.astrachat.core

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class McpClientTest {
    @Test fun promptInsertPrefersUserMessages() {
        assertEquals("hi", promptInsertText(listOf(McpPromptMessage("assistant", "sys"), McpPromptMessage("user", "hi"))))
        assertEquals("a\n\nb", promptInsertText(listOf(McpPromptMessage("user", "a"), McpPromptMessage("assistant", "x"), McpPromptMessage("user", "b"))))
        assertEquals("only assistant", promptInsertText(listOf(McpPromptMessage("assistant", "only assistant"))))
        assertEquals("", promptInsertText(emptyList()))
    }
}
