plugins {
    id("magisk.android.application")
    id("smscode.android.common")
    id("magisk.app.signing")
    id("magisk.app.packaging")
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.ksp)
    id(libs.plugins.kotlin.compose.get().pluginId)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

val versionNameStr = providers.gradleProperty("versionName")
    .orElse(libs.versions.versionName)
    .get()
val versionCodeInt = providers.gradleProperty("versionCode")
    .map { requireNotNull(it.toIntOrNull()) { "Invalid -PversionCode=$it" } }
    .orElse(libs.versions.versionCode.map { it.toInt() })
    .get()
val ndkVersionStr = libs.versions.ndk.get()
val relayDownloadUrl = "https://github.com/magisk317/xinyi-relay"
val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false
val mobileEntitlementEnforced = true
val mobileEntitlementApiOrigin = findProperty("mobileEntitlementApiOrigin")?.toString()
    ?: "https://activate.magisk317.qzz.io"
val mobileEntitlementSigningPublicJwk = findProperty("mobileEntitlementSigningPublicJwk")?.toString()
    ?: """{"kty":"EC","x":"4kPpwUt1wFRuF3EqGq6q57J3YmANf7wyiNH90FNkAbI","y":"U4-E1XK6LjWIXMFNEoSAoik7nD1S07BDb7qAipQd4Ts","crv":"P-256","alg":"ES256","use":"sig","kid":"mobile-entitlement-1"}"""
val mobileEntitlementGoogleWebClientId = findProperty("mobileEntitlementGoogleWebClientId")?.toString()
    ?: "87389120666-vom72bgs4me1eijuiufo0rnug528n6ce.apps.googleusercontent.com"
fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
val generatedSmsCodeRulesAssetsDir = layout.buildDirectory.dir("generated/smscodeRulesAssets")
val syncSmsCodeRulesAssets = tasks.register<Sync>("syncSmsCodeRulesAssets") {
    val rulesRoot = rootProject.layout.projectDirectory.dir("smscode/rules")
    from(rulesRoot.dir("_meta")) {
        into("meta")
    }
    from(rulesRoot.dir("rules")) {
        into("rules")
    }
    into(generatedSmsCodeRulesAssetsDir.map { it.dir("smscode-rules") })
}

android {
    namespace = "com.github.tianma8023.xposed.smscode"
    ndkVersion = ndkVersionStr


    androidResources {
        localeFilters.addAll(listOf("en", "zh-rCN", "zh-rTW"))
    }

    val gitCommitHash = providers.exec {
        commandLine("git", "-C", projectDir, "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()

    defaultConfig {
        applicationId = "com.github.tianma8023.xposed.smscode"
        versionCode = versionCodeInt
        versionName = versionNameStr

        buildConfigField("String", "LOG_TAG", "\"XSmsCode\"")
        buildConfigField("String", "COMMIT_HASH", "\"$gitCommitHash\"")
        buildConfigField("int", "MODULE_VERSION", "$versionCodeInt")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
        buildConfigField("String", "B_DOWNLOAD_URL", "\"$relayDownloadUrl\"")
        buildConfigField("boolean", "MOBILE_ENTITLEMENT_ENFORCED", mobileEntitlementEnforced.toString())
        buildConfigField("String", "MOBILE_ENTITLEMENT_API_ORIGIN", buildConfigString(mobileEntitlementApiOrigin))
        buildConfigField("String", "MOBILE_ENTITLEMENT_SIGNING_PUBLIC_JWK", buildConfigString(mobileEntitlementSigningPublicJwk))
        buildConfigField("String", "MOBILE_ENTITLEMENT_GOOGLE_WEB_CLIENT_ID", buildConfigString(mobileEntitlementGoogleWebClientId))
    }

    productFlavors {
        getByName("play") {
            buildConfigField("String", "MOBILE_ENTITLEMENT_CHANNEL", "\"play\"")
        }
        getByName("github") {
            buildConfigField("String", "MOBILE_ENTITLEMENT_CHANNEL", "\"sideload\"")
        }
        getByName("fdroid") {
            buildConfigField("String", "MOBILE_ENTITLEMENT_CHANNEL", "\"sideload\"")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(generatedSmsCodeRulesAssetsDir.get().asFile.absolutePath)
        }
    }
    packaging {
        resources {
            excludes += "**/*.kotlin_*"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            merges += "META-INF/xposed/*"
        }
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(
                runCatching {
                    org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString())
                }.getOrElse { org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26 }
            )
        }
    }
}

tasks.named("preBuild") {
    dependsOn(syncSmsCodeRulesAssets)
}

dependencies {
    implementation("com.magisk317.mobile:entitlement-android:0.1.12")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":core"))
    implementation(project(":hook"))
    implementation(project(":runtime"))
    implementation(project(":smscode-core:domain"))
    implementation(project(":smscode-core:runtime"))
    implementation(project(":smscode-core:verification"))
    implementation(project(":smscode-core:hook"))
    implementation(project(":magisk-xposed-kit"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.windowSizeClass)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    implementation(libs.timber)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.collections.immutable)
    add("playImplementation", libs.androidx.credential.core)
    add("playImplementation", libs.androidx.credential.play.services.auth)
    add("playImplementation", libs.google.id)
}

val verifyNoLocalVerificationEngine = tasks.register("verifyNoLocalVerificationEngine") {
    group = "verification"
    description = "Ensure app does not reintroduce local verification engine infrastructure already shared in smscode-core."

    val bannedFiles = listOf(
        "src/main/java/com/github/magisk317/smscode/xp/hook/code/InboundSmsBlocker.kt",
        "src/main/java/com/github/magisk317/smscode/xp/hook/code/InboundSmsMethodInvoker.kt",
        "src/main/java/com/github/magisk317/smscode/xp/hook/code/SmsIntentHookSupport.kt",
    )
    val hookSourceRoot = layout.projectDirectory.dir("src/main/java/com/github/magisk317/smscode/xp")
    val bannedHookRegexes = listOf(
        Regex("""^\s*import\s+com\.github\.magisk317\.smscode\.ui\.record\."""),
    )
    val projectRoot = layout.projectDirectory.asFile

    inputs.files(bannedFiles.map { layout.projectDirectory.file(it) })
    inputs.dir(hookSourceRoot)

    doLast {
        val bannedFileViolations = bannedFiles.filter { projectRoot.resolve(it).exists() }
        val hookImportViolations = hookSourceRoot
            .asFileTree
            .matching { include("**/*.kt") }
            .files
            .flatMap { source ->
                source.readLines().mapIndexedNotNull { index, line ->
                    if (bannedHookRegexes.any { regex -> regex.containsMatchIn(line) }) {
                        "${source.relativeTo(projectRoot)}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
        val violations = bannedFileViolations + hookImportViolations
        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("App must not reintroduce local verification engine infrastructure:")
                    violations.forEach { appendLine(it) }
                },
            )
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyNoLocalVerificationEngine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
