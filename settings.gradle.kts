pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(
    ":app",
    ":storage",
    ":core",
    ":smscode-core:smscode-xposed-core",
    ":smscode-core:smscode-domain",
    ":smscode-core:smscode-verification-core",
    ":xposed-stub",
    ":magisk-ui-kit",
)
