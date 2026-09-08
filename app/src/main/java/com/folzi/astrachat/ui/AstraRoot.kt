package com.folzi.astrachat.ui

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import kotlinx.coroutines.launch

/** MainActivity starts the window secured; this applies the stored screenshot preference as soon as settings load. */
@Composable
private fun ApplyScreenshotPolicy(allowed: Boolean) {
    val window = LocalActivity.current?.window
    LaunchedEffect(window, allowed) {
        if (window == null) return@LaunchedEffect
        if (allowed) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
fun AstraRoot(vm: AstraViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    ApplyScreenshotPolicy(settings.allowScreenshots)
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    AstraTheme(settings) {
        Surface(Modifier.fillMaxSize()) {
            BoxWithConstraints {
                val wide = maxWidth >= 840.dp
                val openMenu = { scope.launch { drawer.open() }; Unit }
                val navigate: (String) -> Unit = { route -> nav.navigate(route) { launchSingleTop = true }; scope.launch { drawer.close() } }
                val history: @Composable () -> Unit = { HistoryPanel(vm, onSelected = { navigate("chat") }, navigate = navigate) }
                val content: @Composable () -> Unit = {
                    NavHost(nav, startDestination = "chat", modifier = Modifier.fillMaxSize()) {
                        composable("chat") { ChatScreen(vm, if (wide) null else openMenu, { navigate("settings") }, { navigate("providers") }) }
                        composable("providers") { ProvidersScreen(vm) { nav.popBackStack() } }
                        composable("settings") { SettingsScreen(vm, { navigate("providers") }) { nav.popBackStack() } }
                        composable("statistics") { StatisticsScreen(vm) { nav.popBackStack() } }
                    }
                }
                if (wide) {
                    Row {
                        Surface(Modifier.width(300.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainer) { history() }
                        Box(Modifier.weight(1f)) { content() }
                    }
                } else {
                    ModalNavigationDrawer(drawerState = drawer, drawerContent = { ModalDrawerSheet { history() } }) { content() }
                }
            }
            notice?.let { message ->
                var details by remember(message) { mutableStateOf(false) }
                AlertDialog(onDismissRequest = { vm.notice.value = null }, title = { Text("Astra Chat") },
                    text = { Column { Text(message.text); if (message.technical.isNotEmpty()) {
                        Action(if (details) "Скрыть детали" else "Технические детали") { details = !details }
                        if (details) Text(message.technical)
                    } } }, confirmButton = { Action("Понятно") { vm.notice.value = null } })
            }
        }
    }
}
