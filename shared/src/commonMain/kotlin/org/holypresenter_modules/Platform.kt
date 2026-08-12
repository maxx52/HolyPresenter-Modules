package org.holypresenter_modules

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform