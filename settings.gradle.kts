pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/eap")
    }
}

include(":app", ":storage", ":core")
