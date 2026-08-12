package org.holypresenter_marketplace.catalog

import kotlinx.serialization.Serializable

@Serializable
data class MarketplaceCatalog(
    val formatVersion: Int = 1,
    val modules: List<MarketplaceModuleInfo> = emptyList()
)

@Serializable
data class MarketplaceModuleInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val icon: String = "📦",
    val category: String = "Другое",
    val apiVersion: String,
    val downloadUrl: String,
    val sha256: String,
    val dependencies: List<MarketplaceDependency> = emptyList()
)

@Serializable
data class MarketplaceDependency(
    val id: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String
)
