package org.holypresenter_marketplace

import androidx.compose.runtime.Composable
import holypresenter.org.platform.api.module.HolyModule
import holypresenter.org.platform.api.module.ModuleContext
import holypresenter.org.platform.api.module.ModuleMetadata
import org.holypresenter_marketplace.ui.MarketplaceWorkspace

class MarketplaceModule : HolyModule {
    private lateinit var context: ModuleContext

    override val metadata = ModuleMetadata(
        id = "marketplace",
        name = "Маркетплейс",
        version = "1.0.0",
        apiVersion = "0.6.0",
        author = "HolyPresenter",
        description = "Установка бесплатных модулей HolyPresenter",
        icon = "🛍️"
    )

    override fun onLoad(context: ModuleContext) {
        this.context = context
    }

    @Composable
    override fun Workspace() {
        MarketplaceWorkspace(context)
    }
}
