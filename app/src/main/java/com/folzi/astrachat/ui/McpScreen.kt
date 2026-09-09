package com.folzi.astrachat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folzi.astrachat.core.*
import com.folzi.astrachat.data.newId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun McpScreen(vm: AstraViewModel, back: () -> Unit, toChat: () -> Unit) {
    val servers by vm.mcpServers.collectAsStateWithLifecycle()
    val overview by vm.mcpOverview.collectAsStateWithLifecycle()
    val busy by vm.working.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<McpServer?>(null) }
    var deletion by remember { mutableStateOf<McpServer?>(null) }
    var prompt by remember { mutableStateOf<Pair<McpServer, McpPrompt>?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("MCP-серверы") }, navigationIcon = { Action("Назад", onClick = back) }, actions = { Action("Добавить") { edit = McpServer(newId(), "", "https://") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Model Context Protocol, транспорт Streamable HTTP (ревизия 2025-06-18): подключение к удалённым и локальным MCP-серверам, обзор инструментов, вставка промптов и ресурсов в черновик чата. Ключ и заголовки зашифрованы Android Keystore. Вызов инструментов моделью появится на следующем этапе.") }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Обращение к MCP-серверу…") }
            items(servers, key = { it.id }) { s ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.name.ifBlank { s.url }, style = MaterialTheme.typography.titleLarge)
                        Text(s.url, style = MaterialTheme.typography.bodySmall)
                        Text(if (vm.hasMcpCredentials(s.id)) "секреты сохранены" else "без сохранённого ключа", style = MaterialTheme.typography.labelSmall)
                        FlowRow {
                            Action("Настроить") { edit = s }
                            Action("Подключить", !busy) { vm.discoverMcp(s) }
                            Action("Удалить") { deletion = s }
                        }
                        if (overview?.serverId == s.id) overview?.let { d -> OverviewPanel(vm, s, d, busy, { p -> prompt = s to p }, toChat) }
                    }
                }
            }
        }
    }
    edit?.let { s -> McpEditor(s, vm) { edit = null } }
    deletion?.let { s ->
        Confirm("Удалить ${s.name.ifBlank { s.url }}?", "Настройки и сохранённые секреты MCP-сервера будут удалены.", { deletion = null }) { vm.deleteMcpServer(s) }
    }
    prompt?.let { (s, p) -> PromptArguments(s, p, vm, { prompt = null }, toChat) }
}

@Composable
private fun OverviewPanel(vm: AstraViewModel, server: McpServer, d: McpDiscovery, busy: Boolean, onPrompt: (McpPrompt) -> Unit, toChat: () -> Unit) {
    HorizontalDivider()
    Text("Сессия: ${d.session.info.name.ifBlank { "сервер" }} ${d.session.info.version} · протокол ${d.session.protocolVersion}", style = MaterialTheme.typography.labelSmall)
    if (d.session.instructions.isNotBlank()) Text(d.session.instructions, style = MaterialTheme.typography.bodySmall)
    SectionLabel("Инструменты: ${d.tools.size}")
    if (d.tools.isEmpty()) Text("Сервер не объявил инструменты или не поддержал tools/list.", style = MaterialTheme.typography.bodySmall)
    d.tools.forEach { t ->
        Text(t.title.ifBlank { t.name }, style = MaterialTheme.typography.titleSmall)
        if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall)
    }
    SectionLabel("Ресурсы: ${d.resources.size}")
    if (d.resources.isEmpty()) Text("Сервер не объявил ресурсы.", style = MaterialTheme.typography.bodySmall)
    d.resources.forEach { r ->
        Text(r.name.ifBlank { r.uri }, style = MaterialTheme.typography.titleSmall)
        Text(r.uri + if (r.mimeType.isNotBlank()) " · ${r.mimeType}" else "", style = MaterialTheme.typography.bodySmall)
        if (r.description.isNotBlank()) Text(r.description, style = MaterialTheme.typography.bodySmall)
        Action("Прочитать и вставить в черновик", !busy) { vm.readMcpResource(server, r.uri, toChat) }
    }
    SectionLabel("Промпты: ${d.prompts.size}")
    if (d.prompts.isEmpty()) Text("Сервер не объявил промпты.", style = MaterialTheme.typography.bodySmall)
    d.prompts.forEach { p ->
        Text(p.title.ifBlank { p.name }, style = MaterialTheme.typography.titleSmall)
        if (p.description.isNotBlank()) Text(p.description, style = MaterialTheme.typography.bodySmall)
        if (p.arguments.isNotEmpty()) Text("Аргументы: " + p.arguments.joinToString(", ") { it.name + if (it.required) "*" else "" }, style = MaterialTheme.typography.labelSmall)
        Action("Вставить в черновик", !busy) { onPrompt(p) }
    }
}

@Composable
private fun PromptArguments(server: McpServer, prompt: McpPrompt, vm: AstraViewModel, close: () -> Unit, toChat: () -> Unit) {
    var values by remember(prompt.name) { mutableStateOf(prompt.arguments.associate { it.name to "" }) }
    val filled = prompt.arguments.filter { it.required }.all { values[it.name].orEmpty().isNotBlank() }
    AlertDialog(onDismissRequest = close, title = { Text(prompt.title.ifBlank { prompt.name }) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (prompt.description.isNotBlank()) Text(prompt.description, style = MaterialTheme.typography.bodySmall)
            if (prompt.arguments.isEmpty()) Text("Промпт без аргументов: текст будет получен с сервера и вставлен в черновик чата.")
            prompt.arguments.forEach { a ->
                TextField(if (a.description.isNotBlank()) "${a.name} — ${a.description}" else a.name, values[a.name].orEmpty(), { v -> values = values + (a.name to v) })
            }
        }
    }, confirmButton = {
        Action("Вставить", filled) { vm.insertMcpPrompt(server, prompt, values.filterValues { it.isNotBlank() }) { close(); toChat() } }
    }, dismissButton = { Action("Отмена", onClick = close) })
}

@Composable
private fun McpEditor(original: McpServer, vm: AstraViewModel, close: () -> Unit) {
    var s by remember(original.id) { mutableStateOf(original) }
    var key by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf(s.timeoutSeconds.toString()) }
    var error by remember { mutableStateOf("") }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(16.dp).fillMaxHeight(0.94f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("Настройка MCP-сервера", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField("Название", s.name, { s = s.copy(name = it) })
                    TextField("URL endpoint, например https://example.com/mcp", s.url, { s = s.copy(url = it.trim()) })
                    OutlinedTextField(key, { key = it }, label = { Text("Новый ключ; пусто — сохранить текущий") },
                        placeholder = { if (vm.hasMcpCredentials(s.id)) Text("••••••••") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(headers, { headers = it }, label = { Text("HTTP headers JSON; пусто — без изменений") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Text("Ключ отправляется как Authorization: Bearer, если заголовки не задают собственный Authorization. Формат заголовков: JSON-объект со строковыми значениями; {} очищает их. Секреты не возвращаются в поля редактора.", style = MaterialTheme.typography.bodySmall)
                    TextField("Таймаут, секунд (10–600)", timeout, { timeout = it })
                    Toggle("Разрешить небезопасный локальный HTTP", s.allowLocalHttp) { s = s.copy(allowLocalHttp = it) }
                    if (s.allowLocalHttp || s.url.startsWith("http:")) Text("Предупреждение: HTTP не защищает сообщения и ключ от перехвата. Разрешены только localhost и частные IPv4-адреса.", color = MaterialTheme.colorScheme.error)
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                }
                Row {
                    Action("Отмена", onClick = close)
                    Action("Сохранить") {
                        try {
                            require(s.name.isNotBlank()); val seconds = timeout.toInt(); require(seconds in 10..600)
                            TransportPolicy.validate(s.url, s.allowLocalHttp)
                            val h = if (headers.isBlank()) null else stringMap(headers)
                            vm.saveMcpServer(s.copy(timeoutSeconds = seconds), key.takeIf { it.isNotEmpty() }, h); close()
                        } catch (_: Exception) { error = "Проверьте URL, разрешение HTTP, имя, таймаут и формат JSON." }
                    }
                }
            }
        }
    }
}
