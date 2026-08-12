plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
    implementation("org.jetbrains.compose.foundation:foundation:1.11.1")
    implementation("org.jetbrains.compose.ui:ui:1.11.1")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.holypresenter:platform-api:0.6.0")
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("marketplace")
    archiveVersion.set("")
}

val holyPresenterModulesDir =
    providers.gradleProperty("holyPresenterModulesDir")
        .map(::file)
        .orElse(layout.projectDirectory.dir("../../HolyPresenter/desktopApp/modules").asFile)

val installModule by tasks.registering(Copy::class) {
    description = "Copies the Marketplace module into HolyPresenter"
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(holyPresenterModulesDir)
}

tasks.named<Jar>("jar") {
    finalizedBy(installModule)
}
