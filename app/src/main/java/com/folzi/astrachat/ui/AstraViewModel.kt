package com.folzi.astrachat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folzi.astrachat.core.*
import com.folzi.astrachat.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

private const val MAX_TOOL_ROUNDS = 8
private const val TOOL_RESULT_CHARS = 32_000

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AstraViewModel @Inject constructor(
    val repository: ChatRepository, private val mcp: McpRepository, private val vault: SecretVault,
    private val settingsStore: SettingsStore, private val gateway: ChatGateway,
) : ViewModel() {
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val chats = repository.chats.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val providers = repository.providers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val models = repository.models.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val usage = repository.usage.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val mcpServers = mcp.servers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val mcpOverview = MutableStateFlow<McpDiscovery?>(null)
    val selected = MutableStateFlow<String?>(null)
    val messages = selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.dao.messages(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val draft = MutableStateFlow("")
    val live = MutableStateFlow<LiveRequest?>(null)
    val notice = MutableStateFlow<Notice?>(null)
    val working = MutableStateFlow(false)
    private val guard = AtomicBoolean(false)
    private var generationJob: Job? = null
    private var draftJob: Job? = null
    private var selectJob: Job? = null

    init { action { repository.initialize(); repository.dao.allChats().firstOrNull()?.let { select(it.id) } } }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { report(error) }
    }
    private fun report(error: Throwable) { val safe = safeFailure(error); notice.value = Notice(safe.kind.messageRu, safe.technical) }
    fun select(id: String) {
        draftJob?.cancel(); selectJob?.cancel()
        val previous = selected.value; val previousDraft = draft.value
        selected.value = id; draft.value = ""
        selectJob = action {
            if (previous != null && previous != id) repository.dao.saveDraft(DraftRow(previous, previousDraft))
            val value = repository.dao.draft(id)?.text.orEmpty()
            if (selected.value == id) draft.value = value
        }
    }
    fun updateDraft(value: String) {
        draft.value = value
        val id = selected.value ?: return
        draftJob?.cancel()
        draftJob = action { delay(250); repository.dao.saveDraft(DraftRow(id, value)) }
    }
    fun newChat() = action {
        val id = repository.createChat(settings.value.providerId, settings.value.modelId)
        select(id)
    }
    fun saveSettings(value: AppSettings) = action { settingsStore.save(value) }
    fun chooseModel(model: Model) = action {
        settingsStore.save(settings.value.copy(providerId = model.providerId, modelId = model.id))
        selected.value?.let { id -> repository.dao.chat(id)?.let { repository.dao.saveChat(it.copy(providerId = model.providerId, modelId = model.id)) } }
    }
    fun rename(chat: ChatRow, title: String) = action { require(title.isNotBlank()); repository.dao.saveChat(chat.copy(title = title.take(120))) }
    fun pin(chat: ChatRow) = action { repository.dao.saveChat(chat.copy(pinned = !chat.pinned)) }
    fun deleteChat(id: String) = action {
        require(live.value?.chatId != id || live.value?.active != true)
        if (selected.value == id) { draftJob?.cancel(); selected.value = null; draft.value = "" }
        repository.removeChat(id)
    }
    fun deleteMessage(row: MessageRow) = action { require(!guard.get()); repository.dao.deleteMessage(row.id) }
    fun branch(row: MessageRow, edit: Boolean = false) = action {
        require(!guard.get())
        val id = repository.branch(row.chatId, row.id, !edit)
        select(id)
        if (edit) {
            selectJob?.join(); updateDraft(row.text)
            notice.value = Notice("Создана отдельная ветка. Измените текст и отправьте сообщение.")
        }
    }
    fun regenerate(row: MessageRow) = action {
        require(!guard.get())
        val history = repository.dao.history(row.chatId)
        val index = history.indexOfFirst { it.id == row.id }
        val user = history.take(index).lastOrNull { it.role == "user" } ?: return@action
        val id = repository.branch(row.chatId, user.id, false)
        select(id); selectJob?.join(); updateDraft(user.text); send()
    }
    fun send() {
        val input = draft.value.trim()
        if (input.isEmpty() || !guard.compareAndSet(false, true)) return
        draftJob?.cancel()
        generationJob = viewModelScope.launch {
            var assistant: MessageRow? = null
            var request: ChatRequest? = null
            var started = 0L; var first: Long? = null; var official = Usage(); var content = ""
            var complete = false; var state = "error"; var category = ""; var timestamp = 0L
            var pendingCalls = emptyList<ToolCall>()
            var ticker: Job? = null
            val sessions = mutableListOf<McpToolSession>()
            suspend fun persist() {
                val row = assistant ?: return
                val req = request ?: return
                val now = System.nanoTime()
                val totals = usageTotals(official, listOf(Turn("system", req.generation.system)) + req.turns, content, complete)
                val stats = UsageRow(row.id, row.chatId, req.provider.id, req.model.id, timestamp,
                    totals.input, totals.output, totals.total, totals.reasoning, totals.cached, totals.estimated,
                    TokenMath.ttft(started, first), (now - started) / 1_000_000,
                    TokenMath.speed(totals.output, first, now), cost(req.model, totals), state)
                repository.checkpoint(row.copy(text = content, state = state, errorCategory = category,
                    toolCalls = if (pendingCalls.isEmpty()) "" else json.encodeToString(pendingCalls)), stats)
                live.value = LiveRequest(row.chatId, totals, started, first, now, state == "generating")
            }
            try {
                val s = settings.value
                val selectedChat = selected.value?.let { repository.dao.chat(it) }
                val providerId = selectedChat?.providerId?.ifBlank { s.providerId } ?: s.providerId
                val modelId = selectedChat?.modelId?.ifBlank { s.modelId } ?: s.modelId
                val p = providers.value.firstOrNull { it.id == providerId } ?: throw SafeFailure(FailureKind.MODEL)
                val m = models.value.firstOrNull { it.providerId == providerId && it.id == modelId } ?: throw SafeFailure(FailureKind.MODEL)
                TransportPolicy.validate(p.baseUrl, p.allowLocalHttp)
                val id = selected.value ?: repository.createChat(p.id, m.id).also { selected.value = it }
                val credentials = withContext(Dispatchers.IO) { vault.read(p.id) }
                val toolRounds = p.protocol == Protocol.CHAT_COMPLETIONS
                var currentTurns = historyTurns(repository.dao.history(id), toolRounds) + Turn("user", input)
                val route = LinkedHashMap<String, Pair<McpToolSession, McpTool>>()
                val tools = mutableListOf<McpTool>()
                if (toolRounds) {
                    val enabledIds = repository.dao.chat(id)?.mcpServerIds.orEmpty().split(',').filter { it.isNotBlank() }.toSet()
                    val sessionById = LinkedHashMap<String, McpToolSession>()
                    val toolsByServer = LinkedHashMap<String, List<McpTool>>()
                    mcpServers.value.filter { it.id in enabledIds }.forEach { srv ->
                        val session = mcp.openSession(srv)
                        sessions += session
                        sessionById[srv.id] = session
                        toolsByServer[srv.id] = session.tools()
                    }
                    exposeTools(toolsByServer).forEach { exposed ->
                        val session = requireNotNull(sessionById[exposed.serverId])
                        route[exposed.name] = session to exposed.tool
                        tools += exposed.tool.copy(name = exposed.name)
                    }
                }
                assistant = repository.appendExchange(id, input, p.id, m.id)
                draft.value = ""
                var rounds = 0
                while (true) {
                    val contextEstimate = usageTotals(Usage(), listOf(Turn("system", s.generation.system)) + currentTurns, "", false).input
                    if (contextEstimate + s.generation.maxOutput > m.contextWindow) throw SafeFailure(FailureKind.CONTEXT)
                    val req = ChatRequest(p, m, credentials, currentTurns, s.generation, tools)
                    request = req
                    ProviderCodec.body(req, p.streaming)
                    content = ""; official = Usage(); first = null; complete = false; pendingCalls = emptyList()
                    started = System.nanoTime(); timestamp = System.currentTimeMillis(); state = "generating"
                    persist()
                    if (ticker == null) ticker = launch { while (isActive) { delay(250); live.update { it?.copy(now = System.nanoTime()) } } }
                    var lastFlush = started
                    var calls = emptyList<ToolCall>()
                    gateway.generate(req).collect { chunk ->
                        if (chunk.text.isNotEmpty() && first == null) first = System.nanoTime()
                        content += chunk.text
                        require(content.length <= 4 * 1024 * 1024)
                        official = official.merge(chunk.usage); complete = complete || chunk.terminal
                        if (chunk.terminal) calls = chunk.toolCalls
                        if (System.nanoTime() - lastFlush > 150_000_000) { persist(); lastFlush = System.nanoTime() }
                    }
                    if (!complete) throw SafeFailure(FailureKind.TRUNCATED)
                    if (calls.isEmpty()) { state = "complete"; break }
                    if (rounds >= MAX_TOOL_ROUNDS) throw SafeFailure(FailureKind.TOOL_LOOP)
                    rounds++
                    pendingCalls = calls; state = "complete"; persist()
                    val row = requireNotNull(assistant)
                    val results = calls.map { call -> executeToolCall(route, call) }
                    assistant = repository.appendToolResults(id, row.position, calls.zip(results))
                    currentTurns = currentTurns + Turn("assistant", content, calls) +
                        calls.mapIndexed { i, call -> Turn("tool", results[i], toolCallId = call.id) }
                }
            } catch (cancel: CancellationException) {
                state = "stopped"; complete = false
            } catch (error: Exception) {
                val safe = safeFailure(error); category = safe.kind.name; state = "error"; complete = false; report(safe)
            } finally {
                ticker?.cancel()
                withContext(NonCancellable) { try { persist() } catch (error: Exception) { report(error) } }
                sessions.forEach { runCatching { it.close() } }
                live.update { it?.copy(active = false) }; guard.set(false)
            }
        }
    }
    fun setChatMcpServers(chatId: String, serverIds: Set<String>) = action {
        repository.dao.chat(chatId)?.let { repository.dao.saveChat(it.copy(mcpServerIds = serverIds.sorted().joinToString(","))) }
    }
    /** Rebuilds provider turns from persisted rows. Tool structures only survive on CHAT_COMPLETIONS chats;
     *  unpaired tool rows and orphan tool_calls are dropped so the wire request stays valid. */
    private fun historyTurns(rows: List<MessageRow>, toolRounds: Boolean): List<Turn> {
        val complete = rows.filter { it.state == "complete" }
        if (!toolRounds) return complete.filter { it.role != "tool" && it.text.isNotBlank() }.map { Turn(it.role, it.text) }
        val resultIds = complete.filter { it.role == "tool" }.map { it.toolCallId }.toSet()
        val callsByRow = complete.associate { row ->
            row.id to if (row.role == "assistant" && row.toolCalls.isNotBlank()) parseToolCalls(row.toolCalls).filter { it.id in resultIds } else emptyList()
        }
        val validIds = callsByRow.values.flatten().map { it.id }.toSet()
        return complete.mapNotNull { row ->
            when {
                row.role == "tool" -> if (row.toolCallId in validIds) Turn("tool", row.text, toolCallId = row.toolCallId) else null
                else -> {
                    val calls = callsByRow.getValue(row.id)
                    if (row.text.isNotBlank() || calls.isNotEmpty()) Turn(row.role, row.text, calls) else null
                }
            }
        }
    }
    /** Executes one model tool call. Local problems become tool results the model can react to;
     *  transport failures propagate as SafeFailure and abort the generation. */
    private suspend fun executeToolCall(route: Map<String, Pair<McpToolSession, McpTool>>, call: ToolCall): String {
        val target = route[call.name] ?: return "Инструмент «${call.name}» недоступен в этой сессии."
        val parsed: JsonObject? = if (call.arguments.isBlank()) JsonObject(emptyMap())
            else runCatching { json.parseToJsonElement(call.arguments) as? JsonObject }.getOrNull()
        if (parsed == null) return "Аргументы вызова «${call.name}» не являются валидным JSON-объектом."
        val result = target.first.callTool(target.second.name, parsed)
        return capToolResult(result.text, result.isError)
    }
    private fun capToolResult(text: String, isError: Boolean): String {
        val capped = if (text.length > TOOL_RESULT_CHARS) text.take(TOOL_RESULT_CHARS) + "\n…[результат обрезан]" else text
        val body = capped.ifBlank { "(пустой ответ инструмента)" }
        return if (isError) "[Ошибка инструмента] $body" else body
    }
    fun stop() { generationJob?.cancel() }
    fun saveProvider(p: Provider, newKey: String?, headers: Map<String, String>?, query: Map<String, String>?) = action {
        TransportPolicy.validate(p.baseUrl, p.allowLocalHttp)
        require(p.name.isNotBlank() && p.timeoutSeconds in 10..600)
        withContext(Dispatchers.IO) {
            val old = vault.read(p.id)
            vault.write(p.id, Credentials(newKey ?: old.key, headers ?: old.headers, query ?: old.query))
        }
        repository.saveProvider(p); notice.value = Notice("Провайдер сохранён.")
    }
    fun deleteKey(id: String) = action { withContext(Dispatchers.IO) { vault.delete(id) }; notice.value = Notice("Ключ и секретные заголовки удалены.") }
    fun hasCredentials(id: String) = vault.exists(id)
    fun deleteProvider(p: Provider) = action {
        require(!guard.get()); withContext(Dispatchers.IO) { vault.delete(p.id) }; repository.dao.deleteProvider(p.id)
    }
    fun copyProvider(p: Provider) = action {
        val copy = p.copy(id = newId(), name = p.name + " · копия")
        repository.saveProvider(copy)
        models.value.filter { it.providerId == p.id }.forEach { repository.saveModel(it.copy(providerId = copy.id)) }
        notice.value = Notice("Профиль и модели скопированы. Ключи и секретные параметры не копировались.")
    }
    fun saveModel(m: Model) = action {
        require(m.id.isNotBlank() && m.contextWindow > 0)
        val inPrice = m.inputPrice
        val outPrice = m.outputPrice
        require(inPrice == null || inPrice >= 0)
        require(outPrice == null || outPrice >= 0)
        repository.saveModel(m)
    }
    fun removeModel(m: Model) = action { require(!guard.get()); repository.dao.deleteModel(m.providerId, m.id) }
    fun fetchModels(p: Provider) = networkAction {
        val credentials = withContext(Dispatchers.IO) { vault.read(p.id) }
        val ids = gateway.models(p, credentials)
        ids.forEach { id ->
            if (models.value.none { it.providerId == p.id && it.id == id }) {
                val params = if (p.protocol == Protocol.CHAT_COMPLETIONS && (id.startsWith("o1") || id.startsWith("o3") || id.startsWith("o4") || id.startsWith("gpt-5"))) setOf("max_completion_tokens", "reasoning_effort") else emptySet()
                repository.saveModel(Model(id, p.id, parameters = params))
            }
        }
        notice.value = Notice("Получено моделей: ${ids.size}. Проверьте context window и параметры выбранной модели.")
    }
    fun testConnection(p: Provider, m: Model) = networkAction {
        val credentials = withContext(Dispatchers.IO) { vault.read(p.id) }
        var received = false; var complete = false
        gateway.generate(ChatRequest(p, m, credentials, listOf(Turn("user", "Reply OK.")), Generation(system = "", maxOutput = 64)))
            .collect { received = received || it.text.isNotEmpty(); complete = complete || it.terminal }
        if (!received || !complete) throw SafeFailure(FailureKind.MALFORMED)
        notice.value = Notice("Подключение проверено: получен ответ модели. Тестовый запрос может тарифицироваться провайдером.")
    }
    private fun networkAction(block: suspend () -> Unit) = action {
        if (working.value) return@action
        working.value = true
        try { block() } finally { working.value = false }
    }
    fun clearHistory() = action { require(!guard.get()); draftJob?.cancel(); repository.clearChats(); selected.value = null; draft.value = "" }
    fun resetStatistics() = action { require(!guard.get()); repository.dao.clearUsage(); live.value = null }
    suspend fun export(markdown: Boolean): String = if (markdown) repository.exportMarkdown() else repository.exportJson()
    fun imported(text: String) = action { require(!guard.get()); val count = repository.importJson(text); notice.value = Notice("Импортировано чатов: $count. Существующие данные сохранены.") }
    fun saveMcpServer(server: McpServer, newKey: String?, headers: Map<String, String>?) = action {
        mcp.save(server, newKey, headers); notice.value = Notice("MCP-сервер сохранён.")
    }
    fun deleteMcpServer(server: McpServer) = action {
        mcp.delete(server)
        if (mcpOverview.value?.serverId == server.id) mcpOverview.value = null
    }
    fun discoverMcp(server: McpServer) = networkAction {
        val discovery = mcp.discover(server)
        mcpOverview.value = discovery
        notice.value = Notice("MCP-сервер подключён: инструментов ${discovery.tools.size}, ресурсов ${discovery.resources.size}, промптов ${discovery.prompts.size}.")
    }
    fun readMcpResource(server: McpServer, uri: String, done: () -> Unit) = networkAction { insertIntoDraft(mcp.readResource(server, uri)); done() }
    fun insertMcpPrompt(server: McpServer, prompt: McpPrompt, arguments: Map<String, String>, done: () -> Unit) = networkAction {
        insertIntoDraft(promptInsertText(mcp.getPrompt(server, prompt.name, arguments))); done()
    }
    fun hasMcpCredentials(id: String) = vault.exists(mcpVaultId(id))
    private suspend fun insertIntoDraft(text: String) {
        require(text.isNotBlank())
        if (selected.value == null) { select(repository.createChat(settings.value.providerId, settings.value.modelId)); selectJob?.join() }
        val current = draft.value
        updateDraft(if (current.isBlank()) text else current.trimEnd() + "\n\n" + text)
    }
    fun ioError() { notice.value = Notice("Не удалось прочитать или записать файл. Проверьте доступ и свободное место.") }
}
data class Notice(val text: String, val technical: String = "")
data class LiveRequest(val chatId: String, val totals: Totals, val start: Long, val first: Long?, val now: Long, val active: Boolean)
fun cost(model: Model, totals: Totals): Double? {
    val input = model.inputPrice ?: return null
    val output = model.outputPrice ?: return null
    return (totals.input * input + totals.output * output) / 1_000_000
}
