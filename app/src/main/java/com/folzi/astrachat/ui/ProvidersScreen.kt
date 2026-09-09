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
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProvidersScreen(vm: AstraViewModel, back: () -> Unit) {
    val providers by vm.providers.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val busy by vm.working.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Provider?>(null) }
    var modelEdit by remember { mutableStateOf<Model?>(null) }
    var deletion by remember { mutableStateOf<Provider?>(null) }
    var test by remember { mutableStateOf<Pair<Provider, Model>?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("Провайдеры") }, navigationIcon = { Action("Назад", onClick = back) }, actions = { Action("Добавить") { edit = Provider(newId(), "", "https://") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Ключи, дополнительные заголовки и query-параметры зашифрованы Android Keystore. Модели можно получить через API или добавить вручную.") }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Обращение к API…") }
            items(providers, key = { it.id }) { p ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${p.icon} ${p.name}", style = MaterialTheme.typography.titleLarge)
                        Text(p.baseUrl, style = MaterialTheme.typography.bodySmall)
                        Text("${p.protocol.name} · ${if (vm.hasCredentials(p.id)) "секреты сохранены" else "без сохранённого ключа"}", style = MaterialTheme.typography.labelSmall)
                        FlowRow {
                            Action("Настроить") { edit = p }
                            Action("Копия") { vm.copyProvider(p) }
                            Action("Получить модели", !busy) { vm.fetchModels(p) }
                            Action("Добавить модель") { modelEdit = Model("", p.id, parameters = emptySet()) }
                            Action("Удалить") { deletion = p }
                        }
                        models.filter { it.providerId == p.id }.forEach { m ->
                            HorizontalDivider()
                            Text(m.id, style = MaterialTheme.typography.titleSmall)
                            Text("Контекст ${m.contextWindow} · input/output $/1M: ${m.inputPrice ?: "—"} / ${m.outputPrice ?: "—"}", style = MaterialTheme.typography.bodySmall)
                            FlowRow {
                                Action("Использовать по умолчанию") { vm.chooseModel(m) }
                                Action("Параметры модели") { modelEdit = m }
                                Action("Проверить подключение", !busy) { test = p to m }
                                Action("Убрать модель") { vm.removeModel(m) }
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { p -> ProviderEditor(p, vm, { edit = null }) }
    modelEdit?.let { m -> ModelEditor(m, { modelEdit = null }) { vm.saveModel(it) } }
    deletion?.let { p -> Confirm("Удалить ${p.name}?", "Настройки, модели и сохранённые секреты будут удалены. Чаты останутся.", { deletion = null }) { vm.deleteProvider(p) } }
    test?.let { (p, m) -> Confirm("Проверить подключение?", "Будет отправлен короткий запрос выбранной модели. Он может тарифицироваться и не включается в статистику чатов.", { test = null }) { vm.testConnection(p, m) } }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderEditor(original: Provider, vm: AstraViewModel, close: () -> Unit) {
    var p by remember(original.id) { mutableStateOf(original) }
    var key by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf(p.timeoutSeconds.toString()) }
    var error by remember { mutableStateOf("") }
    var deleteKey by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(16.dp).fillMaxHeight(0.94f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("Настройка провайдера", style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField("Название", p.name, { p = p.copy(name = it) })
                    TextField("Иконка (символ или emoji)", p.icon, { p = p.copy(icon = it.take(16)) })
                    TextField("Base URL, включая /v1 или /v1beta", p.baseUrl, { p = p.copy(baseUrl = it.trim()) })
                    Text("Протокол", style = MaterialTheme.typography.titleSmall)
                    Protocol.entries.forEach { protocol ->
                        Row { RadioButton(p.protocol == protocol, { p = p.copy(protocol = protocol) }); Action(protocol.name) { p = p.copy(protocol = protocol) } }
                    }
                    TextField("Endpoint относительно Base URL (необязательно)", p.endpoint, { p = p.copy(endpoint = it) })
                    TextField("Endpoint списка моделей (по умолчанию models)", p.modelsEndpoint, { p = p.copy(modelsEndpoint = it) })
                    OutlinedTextField(key, { key = it }, label = { Text("Новый API-ключ; пусто — сохранить текущий") },
                        placeholder = { if (vm.hasCredentials(p.id)) Text("••••••••") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Action("Удалить ключ и секретные параметры") { deleteKey = true }
                    OutlinedTextField(headers, { headers = it }, label = { Text("HTTP headers JSON; пусто — без изменений") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(query, { query = it }, label = { Text("Query JSON; пусто — без изменений") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Text("Формат: JSON-объект со строковыми значениями. {} очищает дополнительные параметры. Секреты не возвращаются в поля редактора.", style = MaterialTheme.typography.bodySmall)
                    TextField("Таймаут, секунд (10–600)", timeout, { timeout = it })
                    Toggle("Потоковая генерация", p.streaming) { p = p.copy(streaming = it) }
                    Toggle("Запрашивать usage в Chat Completions SSE", p.sendStreamUsage) { p = p.copy(sendStreamUsage = it) }
                    Toggle("Разрешить небезопасный локальный HTTP", p.allowLocalHttp) { p = p.copy(allowLocalHttp = it) }
                    if (p.allowLocalHttp || p.baseUrl.startsWith("http:")) Text("Предупреждение: HTTP не защищает сообщения и API-ключ от перехвата. Разрешены только localhost и частные IPv4-адреса. Для HTTPS сертификаты всегда проверяются.", color = MaterialTheme.colorScheme.error)
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                }
                Row {
                    Action("Отмена", onClick = close)
                    Action("Сохранить") {
                        try {
                            require(p.name.isNotBlank()); val seconds = timeout.toInt(); require(seconds in 10..600)
                            TransportPolicy.validate(p.baseUrl, p.allowLocalHttp)
                            val h = if (headers.isBlank()) null else stringMap(headers)
                            val q = if (query.isBlank()) null else stringMap(query)
                            vm.saveProvider(p.copy(timeoutSeconds = seconds), key.takeIf { it.isNotEmpty() }, h, q); close()
                        } catch (_: Exception) { error = "Проверьте URL, разрешение HTTP, имя, таймаут и формат JSON." }
                    }
                }
            }
        }
    }
    if (deleteKey) Confirm("Удалить сохранённые секреты?", "Будут удалены API-ключ, секретные заголовки и query-параметры.", { deleteKey = false }) { vm.deleteKey(p.id) }
}
internal fun stringMap(value: String): Map<String, String> {
    val parsed = json.parseToJsonElement(value).jsonObject
    require(parsed.values.all { it is JsonPrimitive && it.isString })
    return parsed.mapValues { it.value.jsonPrimitive.content }
}
@Composable
private fun ModelEditor(initial: Model, close: () -> Unit, save: (Model) -> Unit) {
    var id by remember { mutableStateOf(initial.id) }
    var context by remember { mutableStateOf(initial.contextWindow.toString()) }
    var input by remember { mutableStateOf(initial.inputPrice?.toString().orEmpty()) }
    var output by remember { mutableStateOf(initial.outputPrice?.toString().orEmpty()) }
    var capabilities by remember { mutableStateOf(initial.parameters) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = close, title = { Text("Модель") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (initial.id.isBlank()) TextField("ID модели", id, { id = it.trim() }) else Text(id)
            TextField("Context window, токенов", context, { context = it })
            TextField("Input USD / 1M (необязательно)", input, { input = it })
            TextField("Output USD / 1M (необязательно)", output, { output = it })
            Text("Включайте только параметры, которые поддерживает эта модель. Получение /models не определяет возможности модели.")
            listOf("temperature", "top_p", "stop", "reasoning_effort", "max_completion_tokens").forEach { capability ->
                Toggle(capability, capability in capabilities) { on -> capabilities = if (on) capabilities + capability else capabilities - capability }
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Action("Сохранить") {
        try {
            val window = context.toInt(); val ip = input.takeIf { it.isNotBlank() }?.toDouble(); val op = output.takeIf { it.isNotBlank() }?.toDouble()
            require(id.isNotBlank() && window > 0 && (ip == null || (ip >= 0 && ip.isFinite())) && (op == null || (op >= 0 && op.isFinite())))
            save(initial.copy(id = id, contextWindow = window, inputPrice = ip, outputPrice = op, parameters = capabilities)); close()
        } catch (_: Exception) { error = "Проверьте ID, положительный контекст и неотрицательные цены." }
    } }, dismissButton = { Action("Отмена", onClick = close) })
}
