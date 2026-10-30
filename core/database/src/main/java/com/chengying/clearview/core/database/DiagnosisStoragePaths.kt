package com.chengying.clearview.core.database

import com.chengying.clearview.core.model.ImageAsset
import com.chengying.clearview.core.model.isValid
import com.chengying.clearview.core.model.isValidDiagnosisId
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.LocalDate

object DiagnosisStoragePaths {
    private const val MAX_RECORD_DIRECTORIES = 60
    private const val MAX_TOTAL_BYTES = 180L * 1024L * 1024L
    const val REPORT_FILE_NAME: String = "report.json"
    const val SOURCE_IMAGE_FILE_NAME: String = "source.jpg"
    const val MASK_IMAGE_FILE_NAME: String = "mask.png"

    // cacheDir exposes /data/user/0 (a symlink to /data/data); strict checks compare canonical paths.
    fun diagnosisRoot(filesDir: File): File = File(canonicalStorageDir(filesDir), "diagnosis")

    private fun canonicalStorageDir(filesDir: File): File =
        runCatching { filesDir.canonicalFile }.getOrElse { filesDir.absoluteFile }

    fun recordDirectory(filesDir: File, recordId: String, today: LocalDate = LocalDate.now()): File {
        return File(diagnosisRoot(filesDir), "${today.year}/%02d/%s".format(today.monthValue, recordId))
    }

    fun sourceImage(filesDir: File, recordId: String, today: LocalDate = LocalDate.now()): File {
        return File(recordDirectory(filesDir, recordId, today), SOURCE_IMAGE_FILE_NAME)
    }

    fun maskImage(filesDir: File, recordId: String, today: LocalDate = LocalDate.now()): File {
        return File(recordDirectory(filesDir, recordId, today), MASK_IMAGE_FILE_NAME)
    }

    fun reportFile(filesDir: File, recordId: String, today: LocalDate = LocalDate.now()): File {
        return File(recordDirectory(filesDir, recordId, today), REPORT_FILE_NAME)
    }

    fun resolveRecordDirectory(filesDir: File, recordId: String): File? {
        val diagnosisRoot = diagnosisRoot(filesDir)
        if (!diagnosisRoot.exists()) return null
        return diagnosisRoot
            .walkTopDown()
            .maxDepth(4)
            .firstOrNull { candidate ->
                candidate.isDirectory &&
                    candidate.name == recordId &&
                    candidate.parentFile?.parentFile?.parentFile == diagnosisRoot
            }
    }

    fun evictIfNeeded(filesDir: File) {
        val diagnosisRoot = diagnosisRoot(filesDir)
        if (!diagnosisRoot.exists()) return

        val recordDirectories = diagnosisRoot
            .walkTopDown()
            .filter { file ->
                file.isDirectory &&
                    file.parentFile?.parentFile?.parentFile == diagnosisRoot
            }
            .toList()
            .distinctBy { it.absolutePath }
            .sortedBy { it.lastModified() }
            .toMutableList()

        fun totalBytes(): Long = recordDirectories.sumOf { dir ->
            dir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        }

        var currentBytes = totalBytes()
        while (recordDirectories.size > MAX_RECORD_DIRECTORIES || currentBytes > MAX_TOTAL_BYTES) {
            if (recordDirectories.isEmpty()) break
            val oldest = recordDirectories.removeAt(0)
            val bytes = oldest.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            oldest.deleteRecursively()
            currentBytes -= bytes
        }
    }

    fun clearAll(filesDir: File) {
        diagnosisRoot(filesDir).takeIf { it.exists() }?.deleteRecursively()
    }
}

/** Hash-named canonical assets. Remote URLs never participate in local path construction. */
object DiagnosisAssetStore {
    @Synchronized
    fun persist(filesDir: File, recordId: String, kind: String, asset: ImageAsset, bytes: ByteArray): String {
        val name = assetName(kind, asset)
        return persistAt(filesDir, recordId, name, asset, bytes)
    }

    @Synchronized
    fun resolve(filesDir: File, recordId: String, kind: String, asset: ImageAsset): String? = runCatching {
        resolveAt(filesDir, recordId, assetName(kind, asset), asset)
    }.getOrNull()

    @Synchronized
    fun persistComparison(
        filesDir: File,
        comparisonId: String,
        recordId: String,
        side: String,
        asset: ImageAsset,
        bytes: ByteArray,
    ): String = persistAt(filesDir, recordId, comparisonAssetName(comparisonId, side, asset), asset, bytes)

    @Synchronized
    fun resolveComparison(
        filesDir: File,
        comparisonId: String,
        recordId: String,
        side: String,
        asset: ImageAsset,
    ): String? = runCatching {
        resolveAt(filesDir, recordId, comparisonAssetName(comparisonId, side, asset), asset)
    }.getOrNull()

    private fun assetName(kind: String, asset: ImageAsset): String {
        require(kind == "source" || kind == "attention") { "Invalid diagnosis asset kind" }
        require(asset.isValid()) { "Invalid asset SHA-256" }
        return "$kind-${asset.sha256!!.lowercase()}.${if (kind == "source") "jpg" else "png"}"
    }

    private fun comparisonAssetName(comparisonId: String, side: String, asset: ImageAsset): String {
        require(isValidDiagnosisId(comparisonId)) { "Invalid comparison identifier" }
        require(side == "current" || side == "prior") { "Invalid comparison side" }
        require(asset.isValid()) { "Invalid asset SHA-256" }
        return "compare-$comparisonId-$side-${asset.sha256!!.lowercase()}.jpg"
    }

    private fun persistAt(filesDir: File, recordId: String, name: String, asset: ImageAsset, bytes: ByteArray): String {
        require(bytes.sha256().equals(asset.sha256, ignoreCase = true)) { "Asset SHA-256 mismatch" }
        val directory = checkedRecordDirectory(filesDir, recordId, create = true)!!
        val target = checkedChild(directory, name)
        if (target.exists()) {
            check(target.isFile && target.sha256().equals(asset.sha256, ignoreCase = true)) {
                "Existing immutable asset failed verification"
            }
        } else {
            atomicDiagnosisWrite(target, bytes)
        }
        DiagnosisStoragePaths.evictIfNeeded(filesDir)
        check(target.isFile && target.sha256().equals(asset.sha256, ignoreCase = true)) {
            "Asset unavailable after cache maintenance"
        }
        return target.absolutePath
    }

    private fun resolveAt(filesDir: File, recordId: String, name: String, asset: ImageAsset): String? {
        val directory = checkedRecordDirectory(filesDir, recordId, create = false) ?: return null
        val target = checkedChild(directory, name)
        return target.takeIf { it.isFile && it.sha256().equals(asset.sha256, ignoreCase = true) }?.absolutePath
    }
}

internal fun checkedDiagnosisRoot(filesDir: File): File {
    val root = DiagnosisStoragePaths.diagnosisRoot(filesDir.canonicalFile).absoluteFile
    require(root.canonicalFile == root && !Files.isSymbolicLink(root.toPath())) { "Unsafe diagnosis cache root" }
    return root
}

internal fun checkedRecordDirectory(filesDir: File, recordId: String, create: Boolean): File? {
    require(isValidDiagnosisId(recordId)) { "Invalid record identifier" }
    val root = checkedDiagnosisRoot(filesDir)
    val existing = if (root.isDirectory) root.walkTopDown().maxDepth(4)
        .onEnter { it.canonicalFile == it.absoluteFile && !Files.isSymbolicLink(it.toPath()) }
        .firstOrNull { it.isDirectory && it.name == recordId && it.parentFile?.parentFile?.parentFile == root }
        else null
    val directory = existing ?: if (create) DiagnosisStoragePaths.recordDirectory(filesDir.canonicalFile, recordId) else return null
    require(directory.canonicalFile == directory.absoluteFile && directory.toPath().startsWith(root.toPath())) {
        "Unsafe diagnosis record directory"
    }
    if (create && !directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create diagnosis record directory")
    require(directory.canonicalFile == directory.absoluteFile) { "Unsafe diagnosis record directory" }
    return directory
}

internal fun checkedChild(directory: File, name: String): File {
    require(!name.contains('/') && !name.contains('\\') && name != "." && name != "..") { "Invalid cache filename" }
    val child = File(directory, name).absoluteFile
    require(directory.canonicalFile == directory.absoluteFile && child.canonicalFile == child &&
        !Files.isSymbolicLink(child.toPath())) { "Unsafe diagnosis cache path" }
    return child
}

@Synchronized
internal fun atomicDiagnosisWrite(target: File, bytes: ByteArray) {
    val directory = target.parentFile ?: throw IOException("Missing cache directory")
    checkedChild(directory, target.name)
    val temporary = File.createTempFile(".diagnosis-", ".tmp", directory)
    try {
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        checkedChild(directory, target.name)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        temporary.delete()
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).hexDigest()

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return digest.digest().hexDigest()
}

private fun ByteArray.hexDigest(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
