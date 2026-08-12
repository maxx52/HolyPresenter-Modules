package org.holypresenter_marketplace.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.prefs.Preferences

internal class ModuleInstaller(
    private val modulesDirectory: File = defaultModulesDirectory()
) {
    fun state(module: MarketplaceModuleInfo): ModuleInstallState {
        val targetFile = File(modulesDirectory, "${module.id}.jar")
        if (!targetFile.isFile) return ModuleInstallState.NOT_INSTALLED
        if (module.id in disabledModuleIds()) return ModuleInstallState.DISABLED
        return if (sha256(targetFile).equals(module.sha256, ignoreCase = true)) {
            ModuleInstallState.INSTALLED
        } else {
            ModuleInstallState.UPDATE_AVAILABLE
        }
    }

    suspend fun install(module: MarketplaceModuleInfo): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(module.downloadUrl.startsWith("https://")) {
                "Загрузка разрешена только по HTTPS"
            }
            modulesDirectory.mkdirs()
            val targetFile = File(modulesDirectory, "${module.id}.jar")
            if (
                targetFile.isFile &&
                sha256(targetFile).equals(module.sha256, ignoreCase = true)
            ) {
                enableModule(module.id)
                return@runCatching "${module.name} уже установлен. Перезапустите HolyPresenter, чтобы включить модуль."
            }
            module.dependencies.forEach(::installDependency)
            val temporaryFile = File.createTempFile("${module.id}-", ".jar", modulesDirectory)
            try {
                download(module.downloadUrl, temporaryFile)
                require(sha256(temporaryFile).equals(module.sha256, ignoreCase = true)) {
                    "Контрольная сумма SHA-256 не совпадает"
                }
                require(isHolyPresenterModule(temporaryFile)) {
                    "В загруженном JAR нет модуля HolyPresenter"
                }
                temporaryFile.copyTo(targetFile, overwrite = true)
                enableModule(module.id)
                "${module.name} установлен. Перезапустите HolyPresenter, чтобы включить его."
            } finally {
                temporaryFile.delete()
            }
        }
    }

    fun uninstall(module: MarketplaceModuleInfo): Result<String> = runCatching {
        disableModule(module.id)
        val targetFile = File(modulesDirectory, "${module.id}.jar")
        when {
            !targetFile.exists() -> "${module.name} отключён."
            targetFile.delete() -> "${module.name} удалён. Перезапустите HolyPresenter."
            else -> {
                targetFile.deleteOnExit()
                "${module.name} отключён и будет удалён после закрытия HolyPresenter."
            }
        }
    }

    private fun download(address: String, destination: File) {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            if (responseCode !in 200..299) error("Сервер вернул HTTP $responseCode")
        }
        connection.inputStream.use { input -> destination.outputStream().use(input::copyTo) }
    }

    private fun installDependency(dependency: MarketplaceDependency) {
        require(dependency.downloadUrl.startsWith("https://")) {
            "Загрузка зависимости ${dependency.id} разрешена только по HTTPS"
        }
        val targetFile = File(modulesDirectory, dependency.fileName)
        if (
            targetFile.isFile &&
            sha256(targetFile).equals(dependency.sha256, ignoreCase = true)
        ) {
            return
        }
        val temporaryFile = File.createTempFile("${dependency.id}-", ".jar", modulesDirectory)
        try {
            download(dependency.downloadUrl, temporaryFile)
            require(sha256(temporaryFile).equals(dependency.sha256, ignoreCase = true)) {
                "Контрольная сумма зависимости ${dependency.id} не совпадает"
            }
            temporaryFile.copyTo(targetFile, overwrite = true)
        } finally {
            temporaryFile.delete()
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    private fun isHolyPresenterModule(file: File): Boolean = JarFile(file).use { archive ->
        archive.getEntry("META-INF/services/holypresenter.org.platform.api.module.HolyModule") != null
    }

    private fun enableModule(moduleId: String) {
        val preferences = Preferences.userRoot().node("org/holypresenter/modules")
        val disabledIds = disabledModuleIds()
        disabledIds -= moduleId
        preferences.put("disabled", disabledIds.sorted().joinToString(","))
    }

    private fun disableModule(moduleId: String) {
        val preferences = Preferences.userRoot().node("org/holypresenter/modules")
        val disabledIds = disabledModuleIds()
        disabledIds += moduleId
        preferences.put("disabled", disabledIds.sorted().joinToString(","))
    }

    private fun disabledModuleIds(): MutableSet<String> =
        Preferences.userRoot().node("org/holypresenter/modules")
            .get("disabled", "")
            .split(',')
            .filter(String::isNotBlank)
            .toMutableSet()

    private companion object {
        fun defaultModulesDirectory(): File {
            val base = System.getenv("LOCALAPPDATA")
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?: File(System.getProperty("user.home"), ".holypresenter")
            return File(base, "HolyPresenter/modules")
        }
    }
}

internal enum class ModuleInstallState {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
    DISABLED
}
