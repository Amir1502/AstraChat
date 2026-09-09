package com.folzi.astrachat.ui

import androidx.compose.foundation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folzi.astrachat.core.*
import com.folzi.astrachat.data.*
import java.time.*
import java.time.format.DateTimeFormatter

private val transcriptWidth = 800.dp
private val gutter = 20.dp

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
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(current?.title ?: "Astra Chat", maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${providers.firstOrNull { it.id == pid }?.name ?: "Провайдер"} · ${model.ifBlank { "выберите модель" }}",
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { menu?.let { Action("Меню", onClick = it) } },
                    actions = { Action("Параметры", onClick = parameters) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Hairline()
            }
        },
        bottomBar = {
            Column {
                Hairline()
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.fillMaxWidth().widthIn(max = transcriptWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (live?.chatId == selected) live?.let { LivePanel(it, usage.filter { u -> u.chatId == selected }.sumOf { u -> u.total }, usage.sumOf { u -> u.total }) }
                            Panel(padding = 8.dp, spacing = 2.dp) {
                                OutlinedTextField(
                                    draft,
                                    vm::updateDraft,
                                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Сообщение" },
                                    placeholder = { Text("Спросите что угодно…") },
                                    maxLines = 7,
                                    shape = MaterialTheme.shapes.medium,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        disabledBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Action("Модель") { picker = true }
                                    SectionLabel("≈ ${contextEstimate} ток. · оценка")
                                    Spacer(Modifier.weight(1f))
                                    if (live?.active == true) {
                                        OutlinedButton(
                                            onClick = vm::stop,
                                            shape = MaterialTheme.shapes.medium,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                            modifier = Modifier.heightIn(min = 44.dp),
                                        ) { Text("Стоп") }
                                    } else {
                                        Button(
                                            onClick = vm::send,
                                            enabled = draft.isNotBlank() && model.isNotBlank(),
                                            shape = MaterialTheme.shapes.medium,
                                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                            modifier = Modifier.heightIn(min = 44.dp),
                                        ) { Text("↑", Modifier.semantics { contentDescription = "Отправить сообщение" }) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) {
                Column(Modifier.align(Alignment.Center).widthIn(max = 620.dp).padding(32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel("ASTRA CHAT")
                    Text("Пространство для мысли", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Ваши модели. Ваши разговоры. История хранится на устройстве; сообщения отправляются только выбранному API.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (models.isEmpty()) PrimaryAction("Настроить провайдера", onClick = providersScreen)
                    else PrimaryAction("Выбрать модель") { picker = true }
                }
            }
            if (messages.isNotEmpty()) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    itemsIndexed(messages, key = { _, row -> row.id }) { index, row ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Column(Modifier.fillMaxWidth().widthIn(max = transcriptWidth).padding(horizontal = gutter)) {
                                Column(Modifier.padding(vertical = 16.dp)) {
                                    TranscriptRow(row, vm, settings.textScale, settings.animations, live?.active != true)
                                }
                                if (index < messages.lastIndex) Hairline()
                            }
                        }
                    }
                    item(key = "transcript-tail") { Spacer(Modifier.height(16.dp)) }
                }
            }
            if (!follow && messages.isNotEmpty()) {
                val scope = rememberCoroutineScope()
                FloatingActionButton(
                    onClick = { follow = true; scope.launch { list.scrollToItem(messages.lastIndex) } },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                ) { Text("Вниз", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Модель", style = MaterialTheme.typography.titleLarge) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(models, key = { it.providerId + "/" + it.id }) { m ->
                        Action("${providers.firstOrNull { it.id == m.providerId }?.name ?: m.providerId} · ${m.id}") { vm.chooseModel(m); picker = false }
                    }
                    if (models.isEmpty()) item {
                        Text("Сначала добавьте модель в настройках провайдера.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { Action("Провайдеры") { picker = false; providersScreen() } },
            dismissButton = { Action("Закрыть") { picker = false } },
        )
    }
}

/** One transcript turn: speaker tag, rendered markdown, state note and quiet actions. No bubbles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranscriptRow(row: MessageRow, vm: AstraViewModel, scale: Float, animations: Boolean, actionsEnabled: Boolean) {
    val context = LocalContext.current
    var delete by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    val mine = row.role == "user"
    Column(Modifier.fillMaxWidth().then(if (animations) Modifier.animateContentSize() else Modifier), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoleLabel(if (mine) "Вы" else "Astra", accented = !mine)
            if (row.state == "generating") {
                Text("генерация…", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (row.text.isNotEmpty()) {
            Markdown(row.text, scale)
        } else if (row.state == "generating") {
            Text("▍", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
        }
        if (row.state in setOf("error", "interrupted", "stopped")) {
            Text(
                when (row.state) {
                    "stopped" -> "Генерация остановлена"
                    "interrupted" -> "Генерация прервана при закрытии приложения"
                    else -> runCatching { FailureKind.valueOf(row.errorCategory).messageRu }.getOrDefault("Запрос завершился с ошибкой")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Action("Копировать") { copy(context, row.text) }
            Action("Действия") { more = !more }
            if (more) {
                if (mine) Action("Редактировать в ветке", actionsEnabled) { vm.branch(row, true) }
                else Action(if (row.state == "complete") "Перегенерировать" else "Повторить", actionsEnabled) { vm.regenerate(row) }
                Action("Новая ветка", actionsEnabled) { vm.branch(row) }
                Action("Удалить", actionsEnabled) { delete = true }
            }
        }
    }
    if (delete) Confirm("Удалить сообщение?", "Это сообщение исчезнет из истории. Статистика выполненного запроса сохранится.", { delete = false }) { vm.deleteMessage(row) }
}

/** Live request telemetry rendered as a monospace status strip. */
@Composable
private fun LivePanel(live: LiveRequest, chatTotal: Long, allTotal: Long) {
    var expanded by remember { mutableStateOf(false) }
    val t = live.totals
    val speed = TokenMath.speed(t.output, live.first, live.now)
    Panel(
        padding = 10.dp,
        spacing = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (live.active) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (live.active) Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(
                "${if (live.active) "Генерация" else "Последний запрос"} · ${number(speed)} ток/с · ${t.total} ток. ${if (t.estimated) "(оценка)" else "(api)"}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Action(if (expanded) "Скрыть" else "Детали") { expanded = !expanded }
        }
        if (expanded) {
            Text(
                "ttft ${TokenMath.ttft(live.start, live.first)?.let { number(it) + " мс" } ?: "ожидание"} · ${number((live.now - live.start) / 1e9)} с",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "input ${t.input} · output ${t.output} · reasoning ${t.reasoning ?: "—"} · cache ${t.cached ?: "—"}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "чат $chatTotal · всё время $allTotal токенов",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    Column(Modifier.fillMaxHeight().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("ASTRA CHAT")
        PrimaryAction("Новый чат", modifier = Modifier.fillMaxWidth()) { vm.newChat(); onSelected() }
        TextField("Поиск по названию", search, { search = it })
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chats.filter { it.title.contains(search, true) }.groupBy { if (it.pinned) "Закреплённые" else Instant.ofEpochMilli(it.updated).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.forEach { (date, group) ->
                item(key = "date-$date") { SectionLabel(date, Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)) }
                items(group, key = { it.id }) { chat ->
                    var more by remember { mutableStateOf(false) }
                    val active = chat.id == selected
                    Panel(
                        padding = 6.dp,
                        spacing = 0.dp,
                        color = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        Action(chat.title) { vm.select(chat.id); onSelected() }
                        Action("Опции чата") { more = !more }
                        if (more) FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Action(if (chat.pinned) "Открепить" else "Закрепить") { vm.pin(chat) }
                            Action("Название") { edit = chat; title = chat.title }
                            Action("Удалить", live?.chatId != chat.id || live?.active != true) { deletion = chat }
                        }
                    }
                }
            }
        }
        Hairline()
        Action("MCP-серверы") { navigate("mcp") }
        Action("Провайдеры") { navigate("providers") }
        Action("Статистика") { navigate("statistics") }
        Action("Настройки") { navigate("settings") }
    }
    edit?.let { chat ->
        AlertDialog(
            onDismissRequest = { edit = null },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Название чата", style = MaterialTheme.typography.titleLarge) },
            text = { TextField("Название", title, { title = it }) },
            confirmButton = { Action("Сохранить", title.isNotBlank()) { vm.rename(chat, title); edit = null } },
            dismissButton = { Action("Отмена") { edit = null } },
        )
    }
    deletion?.let { chat -> Confirm("Удалить чат?", "Все сообщения этого чата будут удалены. Общая статистика сохранится.", { deletion = null }) { vm.deleteChat(chat.id) } }
}
