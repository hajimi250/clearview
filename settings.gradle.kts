import java.util.Properties

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
    }
}

fun isValidSdkDir(path: String?): Boolean = !path.isNullOrBlank() && file(path).isDirectory

fun resolveSdkDirCandidate(): String? {
    val envCandidates = listOf(
        System.getenv("ANDROID_SDK_ROOT"),
        System.getenv("ANDROID_HOME"),
    )
    envCandidates.firstOrNull(::isValidSdkDir)?.let { return it }

    val osName = System.getProperty("os.name").lowercase()
    val userHome = System.getProperty("user.home")
    val userName = System.getProperty("user.name")
    val platformCandidates = buildList {
        if (osName.contains("windows")) {
            System.getenv("LOCALAPPDATA")?.let { add("$it\\Android\\Sdk") }
            add("C:\\Users\\$userName\\AppData\\Local\\Android\\Sdk")
        } else {
            add("$userHome/Android/Sdk")
            add("/mnt/c/Users/$userName/AppData/Local/Android/Sdk")
            add("/mnt/c/Users/yyj/AppData/Local/Android/Sdk")
        }
    }
    return platformCandidates.firstOrNull(::isValidSdkDir)
}

fun syncLocalSdkDir() {
    val localPropertiesFile = file("local.properties")
    val properties = Properties()
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(properties::load)
    }
    val currentSdkDir = properties.getProperty("sdk.dir")
    if (isValidSdkDir(currentSdkDir)) return

    val sdkDir = resolveSdkDirCandidate() ?: return
    if (currentSdkDir == sdkDir) return
    properties.setProperty("sdk.dir", sdkDir)
    localPropertiesFile.outputStream().use { output ->
        properties.store(output, "Local SDK path managed per platform")
    }
}

syncLocalSdkDir()

rootProject.name = "ChengYing"

include(":app")
include(":core:designsystem")
include(":core:model")
include(":core:network")
include(":core:database")
include(":feature:home")
include(":feature:capture")
include(":feature:result")
include(":feature:history")
include(":feature:knowledge")
include(":feature:assistant")
include(":feature:settings")
