package com.folzi.astrachat.ui

import android.annotation.SuppressLint
import com.folzi.astrachat.core.*
import com.folzi.astrachat.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import androidx.lifecycle.viewModelScope
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import kotlinx.serialization.json.*

@OptIn(ExperimentalCoroutinesApi::class)
class AstraViewModelTest {
    @Test fun newChatAndDebouncedDraftUseRepository() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mock(ChatRepository::class.java); val dao = mock(AstraDao::class.java)
        val store = mock(SettingsStore::class.java); val vault = mock(SecretVault::class.java); val api = mock(ChatGateway::class.java)
        `when`(repo.dao).thenReturn(dao)
        `when`(repo.chats).thenReturn(flowOf(emptyList()))
        `when`(repo.providers).thenReturn(flowOf(emptyList()))
        `when`(repo.models).thenReturn(flowOf(emptyList()))
        `when`(repo.usage).thenReturn(flowOf(emptyList()))
        val mcpRepo = mock(McpRepository::class.java)
        `when`(mcpRepo.servers).thenReturn(flowOf(emptyList()))
        `when`(store.settings).thenReturn(flowOf(AppSettings()))
        `when`(dao.allChats()).thenReturn(emptyList())
        `when`(dao.messages(anyString())).thenReturn(flowOf(emptyList()))
        `when`(dao.draft(anyString())).thenReturn(null)
        `when`(dao.chat(anyString())).thenReturn(null)
        `when`(repo.createChat("openai", "")).thenReturn("chat-1")
        val vm = AstraViewModel(repo, mcpRepo, vault, store, api)
        try {
            runCurrent(); vm.newChat(); runCurrent()
            assertEquals("chat-1", vm.selected.value)
            vm.updateDraft("first"); advanceTimeBy(100); vm.updateDraft("latest")
            advanceTimeBy(251); runCurrent()
            verify(dao).saveDraft(DraftRow("chat-1", "latest"))
            verify(dao, never()).saveDraft(DraftRow("chat-1", "first"))
            vm.send(); runCurrent()
            assertNotNull(vm.notice.value); assertEquals("category=MODEL", vm.notice.value!!.technical)
            verifyNoInteractions(api)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun codeFenceParserAndCostAreRealCalculations() {
        val parts = splitCode("Text\n```kotlin\nval x = 1\n```\nMore")
        assertEquals(3, parts.size); assertTrue(parts[1].code); assertEquals("val x = 1", parts[1].text)
        val totals = Totals(1_000_000, 500_000, 1_500_000, null, null, false)
        assertEquals(4.0, cost(Model("m", "p", inputPrice = 2.0, outputPrice = 4.0), totals)!!, 0.0)
        assertNull(cost(Model("m", "p"), totals))
    }
    @Test fun mcpDiscoveryFailureReportsSafeNotice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mock(ChatRepository::class.java); val dao = mock(AstraDao::class.java)
        val store = mock(SettingsStore::class.java); val vault = mock(SecretVault::class.java); val api = mock(ChatGateway::class.java)
        val mcpRepo = mock(McpRepository::class.java)
        `when`(repo.dao).thenReturn(dao)
        `when`(repo.chats).thenReturn(flowOf(emptyList()))
        `when`(repo.providers).thenReturn(flowOf(emptyList()))
        `when`(repo.models).thenReturn(flowOf(emptyList()))
        `when`(repo.usage).thenReturn(flowOf(emptyList()))
        `when`(mcpRepo.servers).thenReturn(flowOf(emptyList()))
        `when`(store.settings).thenReturn(flowOf(AppSettings()))
        `when`(dao.allChats()).thenReturn(emptyList())
        `when`(mcpRepo.discover(McpServer("s", "Test", "https://example.com/mcp"))).thenAnswer { throw SafeFailure(FailureKind.MCP) }
        val vm = AstraViewModel(repo, mcpRepo, vault, store, api)
        try {
            runCurrent()
            vm.discoverMcp(McpServer("s", "Test", "https://example.com/mcp")); advanceUntilIdle()
            assertEquals("category=MCP", vm.notice.value?.technical)
            assertFalse(vm.working.value); assertNull(vm.mcpOverview.value)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    @Test fun insertMcpPromptCreatesChatWhenNoneSelectedAndAppendsDraft() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repo = mock(ChatRepository::class.java); val dao = mock(AstraDao::class.java)
        val store = mock(SettingsStore::class.java); val vault = mock(SecretVault::class.java); val api = mock(ChatGateway::class.java)
        val mcpRepo = mock(McpRepository::class.java)
        `when`(repo.dao).thenReturn(dao)
        `when`(repo.chats).thenReturn(flowOf(emptyList()))
        `when`(repo.providers).thenReturn(flowOf(emptyList()))
        `when`(repo.models).thenReturn(flowOf(emptyList()))
        `when`(repo.usage).thenReturn(flowOf(emptyList()))
        `when`(mcpRepo.servers).thenReturn(flowOf(emptyList()))
        `when`(store.settings).thenReturn(flowOf(AppSettings()))
        `when`(dao.allChats()).thenReturn(emptyList())
        `when`(dao.messages(anyString())).thenReturn(flowOf(emptyList()))
        `when`(dao.draft(anyString())).thenReturn(null)
        `when`(repo.createChat("openai", "")).thenReturn("chat-9")
        `when`(mcpRepo.getPrompt(McpServer("s", "T", "https://example.com/mcp"), "p", emptyMap())).thenReturn(
            listOf(McpPromptMessage("user", "Первый"), McpPromptMessage("assistant", "ignored"), McpPromptMessage("user", "Второй")),
        )
        val vm = AstraViewModel(repo, mcpRepo, vault, store, api)
        try {
            runCurrent()
            var navigated = false
            vm.insertMcpPrompt(McpServer("s", "T", "https://example.com/mcp"), McpPrompt("p"), emptyMap()) { navigated = true }
            advanceUntilIdle()
            assertTrue(navigated)
            assertEquals("chat-9", vm.selected.value)
            assertEquals("Первый\n\nВторой", vm.draft.value)
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
    private class FakeGateway(private val rounds: List<List<Chunk>>) : ChatGateway {
        val requests = mutableListOf<ChatRequest>()
        override fun generate(request: ChatRequest): Flow<Chunk> {
            requests += request
            return rounds[(requests.size - 1).coerceAtMost(rounds.lastIndex)].asFlow()
        }
        override suspend fun models(provider: Provider, credentials: Credentials): List<String> = emptyList()
    }
    // Mockito matchers return null, which Kotlin non-null parameters reject at the call site.
    // These helpers register the matcher and return a non-null dummy instead.
    @SuppressLint("CheckResult") // matcher registration: the returned dummy is intentionally discarded
    private fun anyMessageRow(): MessageRow {
        any(MessageRow::class.java)
        return MessageRow("", "", 0, "", "")
    }
    @SuppressLint("CheckResult")
    private fun anyUsageRow(): UsageRow {
        any(UsageRow::class.java)
        return UsageRow("", "", "", "", 0, 0, 0, 0, null, null, false, null, 0, 0.0, null)
    }
    @SuppressLint("CheckResult")
    private fun anyServer(): McpServer {
        any(McpServer::class.java)
        return McpServer("", "", "https://example.com")
    }
    @SuppressLint("CheckResult")
    private fun anyToolResults(): List<Pair<ToolCall, String>> {
        anyList<Pair<ToolCall, String>>()
        return emptyList()
    }
    // The generation ticker re-schedules forever on the virtual clock and vault.read hops through
    // the real IO dispatcher, so settle with bounded real-time polling instead of advanceUntilIdle.
    private suspend fun TestScope.awaitSettled(vm: AstraViewModel, timeoutMs: Long = 15_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (vm.live.value?.active != false) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("generation did not settle")
            advanceTimeBy(100); runCurrent()
            withContext(Dispatchers.Default) { delay(5) }
        }
        runCurrent()
    }
    private val toolProvider = Provider("openai", "OpenAI", "https://api.openai.com/v1")
    private val toolModel = Model("gpt-test", "openai", contextWindow = 128_000, parameters = setOf())
    private val toolServer = McpServer("srv-1", "Test MCP", "https://mcp.example/mcp")
    private val weatherTool = McpTool("get weather", "Weather", "Weather by city", buildJsonObject { put("type", "object") })
    private val weatherCall = ToolCall("call_1", "get_weather", """{"city":"Moscow"}""")
    private val weatherArgs = buildJsonObject { put("city", "Moscow") }

    private suspend fun toolFixture(gateway: ChatGateway): Quintuple {
        val repo = mock(ChatRepository::class.java); val dao = mock(AstraDao::class.java)
        val store = mock(SettingsStore::class.java); val vault = mock(SecretVault::class.java)
        val mcpRepo = mock(McpRepository::class.java); val session = mock(McpToolSession::class.java)
        `when`(repo.dao).thenReturn(dao)
        `when`(repo.chats).thenReturn(flowOf(emptyList()))
        `when`(repo.providers).thenReturn(flowOf(listOf(toolProvider)))
        `when`(repo.models).thenReturn(flowOf(listOf(toolModel)))
        `when`(repo.usage).thenReturn(flowOf(emptyList()))
        `when`(mcpRepo.servers).thenReturn(flowOf(listOf(toolServer)))
        `when`(store.settings).thenReturn(flowOf(AppSettings()))
        val chat = ChatRow("chat-1", "Чат", providerId = "openai", modelId = "gpt-test", mcpServerIds = "srv-1")
        `when`(dao.allChats()).thenReturn(listOf(chat))
        `when`(dao.chat(anyString())).thenReturn(chat)
        `when`(dao.messages(anyString())).thenReturn(flowOf(emptyList()))
        `when`(dao.draft(anyString())).thenReturn(null)
        `when`(dao.history(anyString())).thenReturn(emptyList())
        `when`(repo.appendExchange("chat-1", "Погода?", "openai", "gpt-test")).thenReturn(MessageRow("a1", "chat-1", 1, "assistant", "", "generating"))
        `when`(repo.appendToolResults("chat-1", 1L, listOf(weatherCall to "Sunny"))).thenReturn(MessageRow("a2", "chat-1", 3, "assistant", "", "generating"))
        return Quintuple(repo, vault, mcpRepo, session, AstraViewModel(repo, mcpRepo, vault, store, gateway))
    }
    private class Quintuple(val repo: ChatRepository, val vault: SecretVault, val mcpRepo: McpRepository, val session: McpToolSession, val vm: AstraViewModel)

    @Test fun toolRoundExecutesCallAndFeedsResultToModel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gateway = FakeGateway(listOf(
            listOf(Chunk(toolCalls = listOf(weatherCall), terminal = true)),
            listOf(Chunk("Погода: солнечно", terminal = true)),
        ))
        val f = toolFixture(gateway)
        val checks = mutableListOf<MessageRow>()
        doAnswer { checks += it.getArgument<MessageRow>(0); null }.`when`(f.repo).checkpoint(anyMessageRow(), anyUsageRow())
        runCurrent()
        `when`(f.vault.read(anyString())).thenReturn(Credentials())
        `when`(f.mcpRepo.openSession(toolServer)).thenReturn(f.session)
        `when`(f.session.tools()).thenReturn(listOf(weatherTool))
        `when`(f.session.callTool("get weather", weatherArgs)).thenReturn(McpToolResult("Sunny", false))
        try {
            f.vm.updateDraft("Погода?"); f.vm.send()
            awaitSettled(f.vm)
            assertNull(f.vm.notice.value)
            assertEquals(2, gateway.requests.size)
            assertEquals(listOf(weatherTool.copy(name = "get_weather")), gateway.requests[0].tools)
            assertEquals(
                listOf(
                    Turn("user", "Погода?"),
                    Turn("assistant", "", listOf(weatherCall)),
                    Turn("tool", "Sunny", toolCallId = "call_1"),
                ),
                gateway.requests[1].turns,
            )
            verify(f.repo).appendToolResults("chat-1", 1L, listOf(weatherCall to "Sunny"))
            val roundRow = checks.first { it.toolCalls.isNotBlank() }
            assertEquals("a1", roundRow.id); assertEquals("complete", roundRow.state)
            assertTrue(roundRow.toolCalls.contains("call_1"))
            assertEquals("Погода: солнечно", checks.last().text)
            assertEquals("complete", checks.last().state); assertEquals("a2", checks.last().id)
            verify(f.session).close()
        } finally { f.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun toolCallErrorResultIsFedBackWithMarker() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gateway = FakeGateway(listOf(
            listOf(Chunk(toolCalls = listOf(weatherCall), terminal = true)),
            listOf(Chunk("Инструмент ошибся", terminal = true)),
        ))
        val f = toolFixture(gateway)
        runCurrent()
        `when`(f.vault.read(anyString())).thenReturn(Credentials())
        `when`(f.mcpRepo.openSession(toolServer)).thenReturn(f.session)
        `when`(f.session.tools()).thenReturn(listOf(weatherTool))
        `when`(f.session.callTool("get weather", weatherArgs)).thenReturn(McpToolResult("boom", true))
        try {
            f.vm.updateDraft("Погода?"); f.vm.send()
            awaitSettled(f.vm)
            assertNull(f.vm.notice.value)
            assertEquals(Turn("tool", "[Ошибка инструмента] boom", toolCallId = "call_1"), gateway.requests[1].turns.last())
        } finally { f.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun toolTransportFailureAbortsGeneration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gateway = FakeGateway(listOf(listOf(Chunk(toolCalls = listOf(weatherCall), terminal = true))))
        val f = toolFixture(gateway)
        val checks = mutableListOf<MessageRow>()
        doAnswer { checks += it.getArgument<MessageRow>(0); null }.`when`(f.repo).checkpoint(anyMessageRow(), anyUsageRow())
        runCurrent()
        `when`(f.vault.read(anyString())).thenReturn(Credentials())
        `when`(f.mcpRepo.openSession(toolServer)).thenReturn(f.session)
        `when`(f.session.tools()).thenReturn(listOf(weatherTool))
        `when`(f.session.callTool("get weather", weatherArgs)).thenAnswer { throw SafeFailure(FailureKind.MCP) }
        try {
            f.vm.updateDraft("Погода?"); f.vm.send()
            awaitSettled(f.vm)
            assertEquals("category=MCP", f.vm.notice.value?.technical)
            assertEquals(1, gateway.requests.size)
            assertEquals("error", checks.last().state); assertEquals("MCP", checks.last().errorCategory)
            verify(f.session).close()
        } finally { f.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun toolRoundsStopAtLimitWithSafeFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gateway = FakeGateway(listOf(listOf(Chunk(toolCalls = listOf(weatherCall), terminal = true))))
        val f = toolFixture(gateway)
        val checks = mutableListOf<MessageRow>()
        doAnswer { checks += it.getArgument<MessageRow>(0); null }.`when`(f.repo).checkpoint(anyMessageRow(), anyUsageRow())
        `when`(f.repo.appendToolResults(anyString(), anyLong(), anyToolResults())).thenReturn(MessageRow("a2", "chat-1", 3, "assistant", "", "generating"))
        runCurrent()
        `when`(f.vault.read(anyString())).thenReturn(Credentials())
        `when`(f.mcpRepo.openSession(toolServer)).thenReturn(f.session)
        `when`(f.session.tools()).thenReturn(listOf(weatherTool))
        `when`(f.session.callTool("get weather", weatherArgs)).thenReturn(McpToolResult("Sunny", false))
        try {
            f.vm.updateDraft("Погода?"); f.vm.send()
            awaitSettled(f.vm)
            assertEquals("category=TOOL_LOOP", f.vm.notice.value?.technical)
            assertEquals(9, gateway.requests.size)
            verify(f.session, times(8)).callTool("get weather", weatherArgs)
            assertEquals("TOOL_LOOP", checks.last().errorCategory)
        } finally { f.vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }

    @Test fun toolsNotAttachedForOtherProtocols() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gateway = FakeGateway(listOf(listOf(Chunk("ok", terminal = true))))
        val repo = mock(ChatRepository::class.java); val dao = mock(AstraDao::class.java)
        val store = mock(SettingsStore::class.java); val vault = mock(SecretVault::class.java)
        val mcpRepo = mock(McpRepository::class.java)
        `when`(repo.dao).thenReturn(dao)
        `when`(repo.chats).thenReturn(flowOf(emptyList()))
        `when`(repo.providers).thenReturn(flowOf(listOf(Provider("anthropic", "Anthropic", "https://api.anthropic.com/v1", Protocol.ANTHROPIC))))
        `when`(repo.models).thenReturn(flowOf(listOf(Model("claude-test", "anthropic", contextWindow = 64_000, parameters = setOf()))))
        `when`(repo.usage).thenReturn(flowOf(emptyList()))
        `when`(mcpRepo.servers).thenReturn(flowOf(listOf(toolServer)))
        `when`(store.settings).thenReturn(flowOf(AppSettings()))
        val chat = ChatRow("chat-1", "Чат", providerId = "anthropic", modelId = "claude-test", mcpServerIds = "srv-1")
        `when`(dao.allChats()).thenReturn(listOf(chat))
        `when`(dao.chat(anyString())).thenReturn(chat)
        `when`(dao.messages(anyString())).thenReturn(flowOf(emptyList()))
        `when`(dao.draft(anyString())).thenReturn(null)
        `when`(dao.history(anyString())).thenReturn(emptyList())
        `when`(repo.appendExchange("chat-1", "Погода?", "anthropic", "claude-test")).thenReturn(MessageRow("a1", "chat-1", 1, "assistant", "", "generating"))
        val vm = AstraViewModel(repo, mcpRepo, vault, store, gateway)
        try {
            runCurrent()
            `when`(vault.read(anyString())).thenReturn(Credentials())
            vm.updateDraft("Погода?"); vm.send()
            awaitSettled(vm)
            assertNull(vm.notice.value)
            assertTrue(gateway.requests.single().tools.isEmpty())
            verify(mcpRepo, never()).openSession(anyServer())
        } finally { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    }
}
