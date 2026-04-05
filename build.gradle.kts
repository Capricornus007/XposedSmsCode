import dev.detekt.gradle.extensions.DetektExtension
import com.adarshr.gradle.testlogger.theme.ThemeType
import org.gradle.api.tasks.Exec

buildscript {
    val nettyVersion = "4.1.129.Final"
    val nettyModules = listOf(
        "netty-codec",
        "netty-codec-http",
        "netty-codec-http2",
        "netty-common",
        "netty-handler",
    )
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        constraints {
            nettyModules.forEach { module ->
                classpath("io.netty:$module:$nettyVersion") {
                    because("Dependabot: declare patched netty modules for build/plugin dependency graph")
                }
            }
        }
    }
    configurations.all {
        resolutionStrategy {
            force(libs.jose4j)
            force(libs.jdom2)
            force(libs.apache.commons.lang3)
            eachDependency {
                if (requested.group == "io.netty") {
                    useVersion(nettyVersion)
                    because("Dependabot: netty CVE fixes (transitive build/test deps)")
                }
            }
        }
    }
}

plugins {
    id("nl.littlerobots.version-catalog-update") version "1.1.0"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.test.logger) apply false
}

kover {
    reports {
        verify {
            rule {
                // Start with a pragmatic threshold and tighten later.
                minBound(60)
            }
        }
    }
}

val catalog = libs

subprojects {
    fun Project.configureDetekt() {
        apply(plugin = "dev.detekt")
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            autoCorrect = true
            parallel = true
            buildUponDefaultConfig = false
            config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        }
        dependencies {
            "detektPlugins"(catalog.detekt.rules.ktlint)
        }
    }

    // Apply kover to all projects
    apply(plugin = "org.jetbrains.kotlinx.kover")

    pluginManager.withPlugin("com.android.application") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("com.android.library") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.android") {
        configureDetekt()
        apply(plugin = "com.adarshr.test-logger")
    }

    // Configure test-logger for all projects
    plugins.withId("com.adarshr.test-logger") {
        configure<com.adarshr.gradle.testlogger.TestLoggerExtension> {
            theme = ThemeType.MOCHA
            showExceptions = true
            showStackTraces = true
            showCauses = true
            showSummary = true
        }
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }

    configurations.all {
        resolutionStrategy {
            force(catalog.jose4j)
            force(catalog.jdom2)
            force(catalog.apache.commons.lang3)
            force(catalog.apache.httpclient)
            // BEGIN AUTO FORCED DEPENDENCIES (managed by workflow)
            // END AUTO FORCED DEPENDENCIES (managed by workflow)
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

tasks.register<Exec>("cleanupGradleCaches") {
    group = "maintenance"
    description = "Remove stale Gradle version caches under the project-local .gradle directory."
    workingDir = rootProject.projectDir
    commandLine("bash", "${rootProject.projectDir}/scripts/cleanup_gradle_caches.sh")
}
