plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val compileSdkInt = libs.versions.compileSdk.get().toInt()
val compileSdkExtensionInt = libs.versions.compileSdkExtension.get().toInt()
val minSdkInt = libs.versions.minSdk.get().toInt()
val allowConflictBypass = findProperty("allowConflictBypass")
    ?.toString()
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "com.github.magisk317.smscode.core"
    compileSdk = compileSdkInt
    compileSdkExtension = compileSdkExtensionInt

    flavorDimensions += listOf("distribution", "xposedApi")
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "false")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "true")
        }
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "true")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_SMS_CHANNEL", "true")
            buildConfigField("boolean", "ALLOW_HTTP_WEBHOOK", "false")
        }
        create("legacy") {
            dimension = "xposedApi"
            buildConfigField("String", "XPOSED_API_FLAVOR", "\"legacy\"")
        }
        create("api101") {
            dimension = "xposedApi"
            buildConfigField("String", "XPOSED_API_FLAVOR", "\"api101\"")
        }
    }

    defaultConfig {
        minSdk = minSdkInt
        buildConfigField("int", "VERSION_CODE", libs.versions.versionCode.get())
        buildConfigField("String", "VERSION_NAME", "\"${libs.versions.versionName.get()}\"")
        buildConfigField("boolean", "IS_LITE_BUILD", "true")
        buildConfigField("boolean", "ALLOW_CONFLICT_BYPASS", allowConflictBypass.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val javaVersion = JavaVersion.toVersion(libs.versions.javaBytecode.get())
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaVersion.toString()))
        }
    }
}

dependencies {
    implementation(project(":storage"))
    implementation(project(":magisk-ui-kit"))
    implementation(project(":smscode-core:smscode-domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.gson)
    implementation(libs.androidx.room.runtime)
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.haze.android)
    implementation(libs.timber)
    implementation(libs.kotlinx.collections.immutable)
    add("playImplementation", libs.play.app.update)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
