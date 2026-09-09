package com.folzi.astrachat.ui

import com.folzi.astrachat.core.*
import com.folzi.astrachat.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import androidx.lifecycle.viewModelScope
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

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
}
