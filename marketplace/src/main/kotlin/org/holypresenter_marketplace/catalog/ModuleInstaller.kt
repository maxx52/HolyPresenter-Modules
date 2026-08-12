package org.holypresenter_marketplace.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.jar.JarFile

internal class ModuleInstaller(
    private val modulesDirectory: File = defaultModulesDirectory()
) {
    suspend fun install(module: MarketplaceModuleInfo): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(module.downloadUrl.startsWith("https://")) {
                "Загрузка разрешена только по HTTPS"
            }
            modulesDirectory.mkdirs()
            val temporaryFile = File.createTempFile("${module.id}-", ".jar", modulesDirectory)
            try {
                download(module.downloadUrl, temporaryFile)
                require(sha256(temporaryFile).equals(module.sha256, ignoreCase = true)) {
                    "Контрольная сумма SHA-256 не совпадает"
                }
                require(isHolyPresenterModule(temporaryFile)) {
                    "В загруженном JAR нет модуля HolyPresenter"
                }
                temporaryFile.copyTo(File(modulesDirectory, "${module.id}.jar"), overwrite = true)
                "${module.name} установлен. Перезапустите HolyPresenter, чтобы включить его."
            } finally {
                temporaryFile.delete()
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

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

    private fun isHolyPresenterModule(file: File): Boolean = JarFile(file).use { archive ->
        archive.getEntry("META-INF/services/holypresenter.org.platform.api.module.HolyModule") != null
    }

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
