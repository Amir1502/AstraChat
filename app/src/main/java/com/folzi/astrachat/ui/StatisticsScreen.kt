package com.folzi.astrachat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folzi.astrachat.data.UsageRow
import java.time.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(vm: AstraViewModel, back: () -> Unit) {
    val all by vm.usage.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    var period by remember { mutableStateOf(0) }
    var reset by remember { mutableStateOf(false) }
    val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
    val cutoff = when (period) { 1 -> midnight; 7 -> midnight.minusDays(6); 30 -> midnight.minusDays(29); else -> null }?.toInstant()?.toEpochMilli() ?: 0
    val data = remember(all, cutoff) { all.filter { it.timestamp >= cutoff } }
    Scaffold(topBar = { TopAppBar(title = { Text("Статистика") }, navigationIcon = { Action("Назад", onClick = back) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { FlowRow { listOf(1 to "Сегодня", 7 to "7 дней", 30 to "30 дней", 0 to "Всё время").forEach { (days, title) ->
                FilterChip(period == days, { period = days }, label = { Text(title) }, modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp))
            } } }
            if (data.isEmpty()) item { Text("Пока нет запросов за этот период. Статистика появится после отправки сообщения.") }
            else {
                item { Summary("Всего", data) }
                item { Text("По провайдерам", style = MaterialTheme.typography.headlineSmall) }
                items(data.groupBy { it.providerId }.entries.toList(), key = { "p-" + it.key }) { (id, group) -> Summary(providers.firstOrNull { it.id == id }?.name ?: id, group) }
                item { Text("По моделям", style = MaterialTheme.typography.headlineSmall) }
                items(data.groupBy { it.providerId + " / " + it.modelId }.entries.toList(), key = { "m-" + it.key }) { (id, group) -> Summary(id, group) }
            }
            item { Text("Скорость: output tokens / время от первого токена до завершения. Среднее — арифметическое по успешно завершённым запросам. При отсутствии usage значения помечены «Оценка». Стоимость приблизительная, без скидок на cached tokens и налогов.") }
            item { Action("Сбросить всю статистику") { reset = true } }
        }
    }
    if (reset) Confirm("Сбросить статистику?", "История чатов останется, счётчики за все периоды будут очищены.", { reset = false }) { vm.resetStatistics() }
}
@Composable
private fun Summary(title: String, rows: List<UsageRow>) {
    val completed = rows.filter { it.state == "complete" }
    val speed = completed.map { it.tokensPerSecond }.average()
    val ttft = rows.mapNotNull { it.ttftMs }.average()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("${rows.sumOf { it.total }} токенов", style = MaterialTheme.typography.headlineMedium)
            Text("Запросов ${rows.size} · завершено ${completed.size}")
            Text("Input ${rows.sumOf { it.input }} · Output ${rows.sumOf { it.output }}")
            Text("Скорость ${number(speed)} ток/с · TTFT ${number(ttft)} мс")
            Text("Оценка: ${rows.count { it.estimated }} запросов · API: ${rows.count { !it.estimated }}")
            if (rows.any { it.cost != null }) Text("≈ USD ${number(rows.sumOf { it.cost ?: 0.0 }, 6)}${if (rows.any { it.cost == null }) " · только модели с заданными ценами" else ""}")
            else Text("Цена не задана", style = MaterialTheme.typography.labelMedium)
        }
    }
}
