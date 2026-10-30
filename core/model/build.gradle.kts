import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val hostBuildToolsVersion = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "36.1.0" else "36.0.0"

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.chengying.clearview.core.model"
    compileSdk = 36
    buildToolsVersion = hostBuildToolsVersion

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}
