import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val hostBuildToolsVersion = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "36.1.0" else "36.0.0"
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val debugBackendBaseUrl = (
    providers.gradleProperty("DEBUG_BACKEND_BASE_URL").orNull
        ?: System.getenv("DEBUG_BACKEND_BASE_URL")
        ?: localProperties.getProperty("debug.backend.baseUrl")
        ?: "http://10.0.2.2:8000/"
).trim().ifEmpty { "http://10.0.2.2:8000/" }

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.chengying.clearview.core.network"
    compileSdk = 36
    buildToolsVersion = hostBuildToolsVersion

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "DEBUG_BACKEND_BASE_URL", debugBackendBaseUrl.asBuildConfigString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.google.gson)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    testImplementation(libs.junit4)
}
