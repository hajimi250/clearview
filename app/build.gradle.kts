import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val hostBuildToolsVersion = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "36.1.0" else "36.0.0"
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val include32BitAbi = providers.gradleProperty("INCLUDE_32BIT_ABI")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false
val appAbiFilters = if (include32BitAbi) {
    listOf("arm64-v8a", "armeabi-v7a")
} else {
    listOf("arm64-v8a")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.chengying.clearview"
    compileSdk = 36
    buildToolsVersion = hostBuildToolsVersion

    defaultConfig {
        applicationId = "com.chengying.clearview"
        minSdk = 26
        targetSdk = 36
        versionCode = 101
        versionName = "V1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    }

    signingConfigs {
        // 口令与 keystore 都在仓库外；文件不存在时 release 仍产出未签名包，不阻断其他环境的构建。
        if (keystorePropertiesFile.exists()) {
            create("clearview") {
                storeFile = keystoreProperties.getProperty("storeFile")?.let(::file)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += appAbiFilters + "x86_64" }
        }
        release {
            ndk { abiFilters += appAbiFilters }
            signingConfig = signingConfigs.findByName("clearview")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":feature:home"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:result"))
    implementation(project(":feature:history"))
    implementation(project(":feature:knowledge"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
