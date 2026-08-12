package org.holypresenter_marketplace.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

internal class CatalogClient(
    private val catalogUrl: String = DEFAULT_CATALOG_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): Result<MarketplaceCatalog> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = connection(catalogUrl)
            connection.inputStream.bufferedReader().use { source ->
                json.decodeFromString<MarketplaceCatalog>(source.readText())
            }
        }
    }

    private fun connection(address: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            if (responseCode !in 200..299) error("Сервер вернул HTTP $responseCode")
        }

    private companion object {
        const val DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/maxx52/HolyPresenter-Modules/main/catalog.json"
    }
}
