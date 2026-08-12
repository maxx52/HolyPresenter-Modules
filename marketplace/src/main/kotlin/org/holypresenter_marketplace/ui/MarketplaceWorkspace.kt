package org.holypresenter_marketplace.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.holypresenter_marketplace.catalog.CatalogClient
import org.holypresenter_marketplace.catalog.MarketplaceCatalog
import org.holypresenter_marketplace.catalog.MarketplaceModuleInfo
import org.holypresenter_marketplace.catalog.ModuleInstaller
import org.holypresenter_marketplace.catalog.ModuleInstallState

@Composable
fun MarketplaceWorkspace() {
    val catalogClient = remember { CatalogClient() }
    val installer = remember { ModuleInstaller() }
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<MarketplaceCatalog?>(null) }
    var search by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var restartRequired by remember { mutableStateOf(false) }
    var moduleStates by remember { mutableStateOf<Map<String, ModuleInstallState>>(emptyMap()) }

    fun refreshStates(currentCatalog: MarketplaceCatalog?) {
        moduleStates = currentCatalog?.modules.orEmpty().associate { module ->
            module.id to installer.state(module)
        }
    }

    fun refresh() {
        scope.launch {
            isLoading = true
            message = null
            isError = false
            catalogClient.load()
                .onSuccess {
                    catalog = it
                    refreshStates(it)
                }
                .onFailure {
                    message = "Не удалось загрузить каталог: ${it.message}"
                    isError = true
                }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    val modules = catalog?.modules.orEmpty().filter { module ->
        search.isBlank() || listOf(module.name, module.description, module.category)
            .any { it.contains(search, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Маркетплейс", style = MaterialTheme.typography.headlineMedium)
                Text("Бесплатные модули HolyPresenter из официального каталога")
            }
            TextButton(enabled = !isLoading, onClick = ::refresh) { Text("Обновить") }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Поиск модулей") },
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp)
        )
        when {
            isLoading && catalog == null -> CircularProgressIndicator()
            message != null -> Text(
                message!!,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            catalog != null && modules.isEmpty() -> Text("В каталоге пока нет опубликованных модулей.")
        }
        if (restartRequired) {
            Button(
                enabled = !isLoading,
                onClick = {
                    RestartController.restart()
                        .onFailure {
                            message = "Не удалось перезапустить HolyPresenter: ${it.message}"
                            isError = true
                        }
                }
            ) {
                Text("Перезапустить HolyPresenter")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(modules, key = { it.id }) { module ->
                ModuleCard(
                    module = module,
                    state = moduleStates[module.id] ?: ModuleInstallState.NOT_INSTALLED,
                    isLoading = isLoading,
                    onInstall = {
                        scope.launch {
                            isLoading = true
                            restartRequired = false
                            installer.install(module)
                                .onSuccess {
                                    message = it
                                    isError = false
                                    restartRequired = true
                                    refreshStates(catalog)
                                }
                                .onFailure {
                                    message = "Не удалось установить модуль: ${it.message}"
                                    isError = true
                                }
                            isLoading = false
                        }
                    },
                    onUninstall = {
                        scope.launch {
                            isLoading = true
                            restartRequired = false
                            installer.uninstall(module)
                                .onSuccess {
                                    message = it
                                    isError = false
                                    restartRequired = true
                                    refreshStates(catalog)
                                }
                                .onFailure {
                                    message = "Не удалось удалить модуль: ${it.message}"
                                    isError = true
                                }
                            isLoading = false
                        }
                    }
                )
            }
        }
    }
}

private object RestartController {
    fun restart(): Result<Unit> = runCatching { relaunchAndExit() }

    private fun relaunchAndExit() {
        val process = ProcessHandle.current().info()
        val command = process.command().orElseThrow {
            IllegalStateException("Не удалось определить команду запуска HolyPresenter")
        }
        val arguments = process.arguments().orElse(emptyArray())
        ProcessBuilder(listOf(command) + arguments).start()
        kotlin.system.exitProcess(0)
    }
}

@Composable
private fun ModuleCard(
    module: MarketplaceModuleInfo,
    state: ModuleInstallState,
    isLoading: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(module.icon, style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.weight(1f)) {
                Text(module.name, style = MaterialTheme.typography.titleLarge)
                Text("${module.category} · ${module.author} · v${module.version}")
                Spacer(Modifier.height(4.dp))
                Text(module.description)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when (state) {
                        ModuleInstallState.NOT_INSTALLED -> "Не установлен"
                        ModuleInstallState.INSTALLED -> "Установлен"
                        ModuleInstallState.UPDATE_AVAILABLE -> "Доступно обновление"
                        ModuleInstallState.DISABLED -> "Отключён"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !isLoading,
                    onClick = onInstall
                ) {
                    Text(
                        when (state) {
                            ModuleInstallState.NOT_INSTALLED -> "Установить"
                            ModuleInstallState.INSTALLED -> "Переустановить"
                            ModuleInstallState.UPDATE_AVAILABLE -> "Обновить"
                            ModuleInstallState.DISABLED -> "Включить"
                        }
                    )
                }
                if (state != ModuleInstallState.NOT_INSTALLED) {
                    OutlinedButton(enabled = !isLoading, onClick = onUninstall) {
                        Text("Удалить")
                    }
                }
            }
        }
    }
}
