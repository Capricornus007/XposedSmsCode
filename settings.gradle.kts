pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":app", ":storage", ":core", ":smscode-core:smscode-xposed-core", ":smscode-core:smscode-domain", ":xposed-stub", ":magisk-ui-kit")
project(":magisk-ui-kit").projectDir = file("../magisk-ui-kit")
