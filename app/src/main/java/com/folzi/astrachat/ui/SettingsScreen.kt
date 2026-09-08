package com.folzi.astrachat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folzi.astrachat.BuildConfig
import com.folzi.astrachat.core.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AstraViewModel, providers: () -> Unit, back: () -> Unit) {
    val stored by vm.settings.collectAsStateWithLifecycle()
    var generation by remember(stored.generation) { mutableStateOf(stored.generation) }
    var temperature by remember(stored.generation) { mutableStateOf(generation.temperature?.toString().orEmpty()) }
    var topP by remember(stored.generation) { mutableStateOf(generation.topP?.toString().orEmpty()) }
    var maxTokens by remember(stored.generation) { mutableStateOf(generation.maxOutput.toString()) }
    var stops by remember(stored.generation) { mutableStateOf(generation.stops.joinToString("\n")) }
    var clear by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val markdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) scope.launch { try { val content = vm.export(true); withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) } ?: error("File access") } } catch (_: Exception) { vm.ioError() } }
    }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { try { val content = vm.export(false); withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) } ?: error("File access") } } catch (_: Exception) { vm.ioError() } }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            require(output.size() + read <= 20 * 1024 * 1024)
                            output.write(buffer, 0, read)
                        }
                        val bytes = output.toByteArray()
                        require(bytes.size <= 20 * 1024 * 1024); bytes.decodeToString()
                    } ?: error("File access")
                }
                vm.imported(text)
            } catch (_: Exception) { vm.ioError() }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }, navigationIcon = { Action("Назад", onClick = back) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section("Оформление") {
                listOf("system" to "Системная", "light" to "Светлая", "dark" to "Тёмная", "amoled" to "AMOLED").forEach { (value, label) ->
                    Row { RadioButton(stored.theme == value, { vm.saveSettings(stored.copy(theme = value)) }); Action(label) { vm.saveSettings(stored.copy(theme = value)) } }
                }
                Toggle("Динамические цвета Android", stored.dynamicColors) { vm.saveSettings(stored.copy(dynamicColors = it)) }
                Toggle("Анимации", stored.animations) { vm.saveSettings(stored.copy(animations = it)) }
                Text("Размер текста сообщений: ${number(stored.textScale.toDouble())}×; системный масштаб сохраняется")
                Slider(stored.textScale, { vm.saveSettings(stored.copy(textScale = it)) }, valueRange = 0.9f..1.5f)
            }
            Section("Приватность экрана") {
                Toggle("Разрешить скриншоты и превью в недавних", stored.allowScreenshots) { vm.saveSettings(stored.copy(allowScreenshots = it)) }
                Text(
                    "По умолчанию окно защищено флагом Android FLAG_SECURE: системные снимки экрана и миниатюры в недавних приложениях блокируются. Включите переключатель, чтобы делать скриншоты переписки — например, для документации или обсуждения ответа. Снимки попадают в галерею и могут быть доступны другим приложениям; настройка применяется сразу, без перезапуска, и не меняется при обновлении приложения.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Section("Генерация по умолчанию") {
                TextField("System prompt", generation.system, { generation = generation.copy(system = it) }, false)
                TextField("Temperature (пусто — не отправлять)", temperature, { temperature = it })
                TextField("Top P (пусто — не отправлять)", topP, { topP = it })
                TextField("Max output tokens", maxTokens, { maxTokens = it })
                TextField("Stop sequences, по одной на строку", stops, { stops = it }, false)
                TextField("Reasoning effort (значение согласно API)", generation.reasoning, { generation = generation.copy(reasoning = it) })
                TextField("Дополнительные параметры JSON", generation.extraJson, { generation = generation.copy(extraJson = it) }, false)
                Text("Не помещайте секреты в JSON генерации. Дополнительный JSON — явное расширение запроса: поддерживаемые поля проверяйте по документации API. Gemini получает их внутри generationConfig.", style = MaterialTheme.typography.bodySmall)
                if (validation.isNotEmpty()) Text(validation, color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    try {
                        val t = temperature.takeIf { it.isNotBlank() }?.toDouble(); val p = topP.takeIf { it.isNotBlank() }?.toDouble(); val max = maxTokens.toInt()
                        require((t == null || t in 0.0..2.0) && (p == null || p in 0.0..1.0) && max > 0)
                        require(json.parseToJsonElement(generation.extraJson) is JsonObject)
                        vm.saveSettings(stored.copy(generation = generation.copy(temperature = t, topP = p, maxOutput = max, stops = stops.lines().filter(String::isNotEmpty))))
                        validation = "Параметры сохранены."
                    } catch (_: Exception) { validation = "Проверьте диапазоны: temperature 0–2, top_p 0–1, max output > 0 и JSON-объект." }
                }) { Text("Сохранить параметры") }
            }
            Section("Провайдер и модель") { Text("${stored.providerId} · ${stored.modelId.ifBlank { "не выбрана" }}"); Action("Управление провайдерами", onClick = providers) }
            Section("Данные на устройстве") {
                Text("Экспорт содержит переписку без API-ключей. Выбранное вами место сохранения может быть доступно другим людям. JSON сохраняет чаты и ветки, но не ключи, настройки, черновики и биллинговую статистику.")
                Action("Экспорт чатов в Markdown") { markdown.launch("AstraChat-chats.md") }
                Action("Экспорт резервной копии JSON") { backup.launch("AstraChat-backup.json") }
                Action("Импорт JSON (до 20 МБ)") { importer.launch(arrayOf("application/json", "text/plain")) }
                Action("Очистить историю") { clear = "history" }
                Action("Сбросить статистику") { clear = "stats" }
            }
            Section("О приложении") {
                Text("Astra Chat ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("Нативный AI-чат без встроенных API-ключей и телеметрии. API-ключи зашифрованы Android Keystore; чаты хранятся в приватной базе приложения. Лицензия MIT.")
                Text("Ответы AI могут быть неточными. Проверяйте важную информацию. Стоимость и приблизительные токены не заменяют счёт провайдера.")
            }
        }
    }
    if (clear.isNotEmpty()) Confirm(if (clear == "history") "Очистить всю историю?" else "Сбросить статистику?", "Это действие нельзя отменить. Во время генерации очистка недоступна.", { clear = "" }) { if (clear == "history") vm.clearHistory() else vm.resetStatistics() }
}
