package com.folzi.astrachat.ui

import androidx.compose.foundation.*
import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folzi.astrachat.core.*
import com.folzi.astrachat.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(vm: AstraViewModel, menu: (() -> Unit)?, parameters: () -> Unit, providersScreen: () -> Unit) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    val current = chats.firstOrNull { it.id == selected }
    val pid = current?.providerId?.ifBlank { settings.providerId } ?: settings.providerId
    val model = current?.modelId?.ifBlank { settings.modelId } ?: settings.modelId
    val contextEstimate = remember(settings.generation.system, messages, draft) {
        TokenMath.estimate(settings.generation.system + messages.joinToString { it.text } + draft)
    }
    val list = rememberLazyListState()
    var follow by remember(selected) { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }.collect { (scrolling, forward) -> if (scrolling) follow = !forward }
    }
    LaunchedEffect(selected, messages.lastOrNull()?.text?.length, messages.size) {
        if (follow && messages.isNotEmpty()) list.scrollToItem(messages.lastIndex)
    }
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        TopAppBar(title = { Column {
            Text(current?.title ?: "Astra Chat", maxLines = 1, style = MaterialTheme.typography.titleMedium)
            Text("${providers.firstOrNull { it.id == pid }?.name ?: "Провайдер"} · ${model.ifBlank { "выберите модель" }}", maxLines = 1, style = MaterialTheme.typography.labelMedium)
        } }, navigationIcon = { menu?.let { Action("Меню", onClick = it) } }, actions = { Action("Параметры", onClick = parameters) })
    }, bottomBar = {
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 900.dp)) {
                if (live?.chatId == selected) live?.let { LivePanel(it, usage.filter { u -> u.chatId == selected }.sumOf { u -> u.total }, usage.sumOf { u -> u.total }) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Action("Модель") { picker = true }
                    Text("≈ ${contextEstimate} ток. · Оценка", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draft, vm::updateDraft, modifier = Modifier.weight(1f).semantics { contentDescription = "Сообщение" },
                        placeholder = { Text("Спросите что угодно…") }, maxLines = 7, shape = MaterialTheme.shapes.large)
                    if (live?.active == true) Button(onClick = vm::stop, modifier = Modifier.heightIn(min = 48.dp)) { Text("Стоп") }
                    else Button(onClick = vm::send, enabled = draft.isNotBlank() && model.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text("↑", Modifier.semantics { contentDescription = "Отправить сообщение" }) }
                }
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("✦", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                Text("Пространство для мысли", style = MaterialTheme.typography.headlineMedium)
                Text("Ваши модели. Ваши разговоры. История хранится на устройстве; сообщения отправляются только выбранному API.")
                if (models.isEmpty()) Button(onClick = providersScreen) { Text("Настроить провайдера") }
                else Action("Выбрать модель") { picker = true }
            }
            if (messages.isNotEmpty()) LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(messages, key = { it.id }) { row ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (row.role == "user") Alignment.CenterEnd else Alignment.CenterStart) {
                        MessageCard(row, vm, settings.textScale, settings.animations, live?.active != true)
                    }
                }
            }
            if (!follow && messages.isNotEmpty()) {
                val scope = rememberCoroutineScope()
                FloatingActionButton(onClick = { follow = true; scope.launch { list.scrollToItem(messages.lastIndex) } }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Text("Вниз") }
            }
        }
    }
    if (picker) AlertDialog(onDismissRequest = { picker = false }, title = { Text("Модель") }, text = {
        LazyColumn { items(models, key = { it.providerId + "/" + it.id }) { m -> Action("${providers.firstOrNull { it.id == m.providerId }?.name ?: m.providerId} · ${m.id}") { vm.chooseModel(m); picker = false } }
            if (models.isEmpty()) item { Text("Сначала добавьте модель в настройках провайдера.") }
        }
    }, confirmButton = { Action("Провайдеры") { picker = false; providersScreen() } }, dismissButton = { Action("Закрыть") { picker = false } })
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageCard(row: MessageRow, vm: AstraViewModel, scale: Float, animations: Boolean, actionsEnabled: Boolean) {
    val context = LocalContext.current
    var delete by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    Surface(Modifier.widthIn(max = 800.dp).then(if (animations) Modifier.animateContentSize() else Modifier), color = if (row.role == "user") MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (row.role == "user") "Вы" else "✦ Astra", style = MaterialTheme.typography.labelLarge)
            if (row.text.isNotEmpty()) Markdown(row.text, scale)
            if (row.state == "generating") Text("Генерация…", style = MaterialTheme.typography.labelMedium)
            if (row.state in setOf("error", "interrupted", "stopped")) {
                Text(when (row.state) { "stopped" -> "Генерация остановлена"; "interrupted" -> "Генерация прервана при закрытии приложения"; else -> runCatching { FailureKind.valueOf(row.errorCategory).messageRu }.getOrDefault("Запрос завершился с ошибкой") }, color = MaterialTheme.colorScheme.error)
            }
            FlowRow {
                Action("Копировать") { copy(context, row.text) }
                Action("Действия") { more = !more }
                if (more) {
                    if (row.role == "user") Action("Редактировать в ветке", actionsEnabled) { vm.branch(row, true) }
                    else Action(if (row.state == "complete") "Перегенерировать" else "Повторить", actionsEnabled) { vm.regenerate(row) }
                    Action("Новая ветка", actionsEnabled) { vm.branch(row) }
                    Action("Удалить", actionsEnabled) { delete = true }
                }
            }
        }
    }
    if (delete) Confirm("Удалить сообщение?", "Это сообщение исчезнет из истории. Статистика выполненного запроса сохранится.", { delete = false }) { vm.deleteMessage(row) }
}
@Composable
private fun LivePanel(live: LiveRequest, chatTotal: Long, allTotal: Long) {
    var expanded by remember { mutableStateOf(false) }
    val t = live.totals
    val speed = TokenMath.speed(t.output, live.first, live.now)
    Column(Modifier.fillMaxWidth()) {
        Action("${if (live.active) "Генерация" else "Последний запрос"} · ${number(speed)} ток/с · ${t.total} ток. ${if (t.estimated) "(Оценка)" else "(API)"}") { expanded = !expanded }
        if (expanded) {
            Text("TTFT: ${TokenMath.ttft(live.start, live.first)?.let { number(it) + " мс" } ?: "ожидание"} · ${number((live.now - live.start) / 1e9)} с", style = MaterialTheme.typography.labelMedium)
            Text("Input ${t.input} · Output ${t.output} · Reasoning ${t.reasoning ?: "—"} · Cache ${t.cached ?: "—"}", style = MaterialTheme.typography.labelMedium)
            Text("Чат $chatTotal · Всё время $allTotal токенов", style = MaterialTheme.typography.labelMedium)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryPanel(vm: AstraViewModel, onSelected: () -> Unit, navigate: (String) -> Unit) {
    val chats by vm.chats.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var edit by remember { mutableStateOf<ChatRow?>(null) }
    var title by remember { mutableStateOf("") }
    var deletion by remember { mutableStateOf<ChatRow?>(null) }
    Column(Modifier.fillMaxHeight().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("✦ Astra Chat", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { vm.newChat(); onSelected() }, modifier = Modifier.fillMaxWidth()) { Text("Новый чат") }
        TextField("Поиск по названию", search, { search = it })
        LazyColumn(Modifier.weight(1f)) {
            chats.filter { it.title.contains(search, true) }.groupBy { if (it.pinned) "Закреплённые" else Instant.ofEpochMilli(it.updated).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.forEach { (date, group) ->
                item(key = "date-$date") { Text(date, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) }
                items(group, key = { it.id }) { chat ->
                    var more by remember { mutableStateOf(false) }
                    Surface(color = if (chat.id == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Action(chat.title) { vm.select(chat.id); onSelected() }
                            Action("Опции чата") { more = !more }
                            if (more) FlowRow {
                                Action(if (chat.pinned) "Открепить" else "Закрепить") { vm.pin(chat) }
                                Action("Название") { edit = chat; title = chat.title }
                                Action("Удалить", live?.chatId != chat.id || live?.active != true) { deletion = chat }
                            }
                        }
                    }
                }
            }
        }
        Action("Провайдеры") { navigate("providers") }
        Action("Статистика") { navigate("statistics") }
        Action("Настройки") { navigate("settings") }
    }
    edit?.let { chat -> AlertDialog(onDismissRequest = { edit = null }, title = { Text("Название чата") }, text = { TextField("Название", title, { title = it }) },
        confirmButton = { Action("Сохранить", title.isNotBlank()) { vm.rename(chat, title); edit = null } }, dismissButton = { Action("Отмена") { edit = null } }) }
    deletion?.let { chat -> Confirm("Удалить чат?", "Все сообщения этого чата будут удалены. Общая статистика сохранится.", { deletion = null }) { vm.deleteChat(chat.id) } }
}
