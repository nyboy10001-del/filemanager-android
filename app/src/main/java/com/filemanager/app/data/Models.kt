package com.filemanager.app.data

import java.util.zip.Deflater

enum class FileCategory(val label: String) {
    FOLDER("Folder"), IMAGES("Images"), VIDEOS("Videos"), AUDIO("Audio"),
    DOCUMENTS("Documents"), APPS("Apps"), ARCHIVES("Archives"), OTHER("Other")
}

data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modified: Long,
    val childCount: Int,
    val category: FileCategory
)

data class StorageStats(val totalBytes: Long, val usedBytes: Long) {
    val freeBytes: Long get() = totalBytes - usedBytes
    val usedFraction: Float get() = if (totalBytes == 0L) 0f else usedBytes.toFloat() / totalBytes
}

data class FileDetails(
    val item: FileItem,
    val typeLabel: String,
    val parentPath: String,
    val totalSizeBytes: Long,
    val dimensions: String?
)

enum class SortOrder(val label: String) {
    NAME("Name"), DATE("Date modified (newest first)"), SIZE("Size (largest first)")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System default"), LIGHT("Light"), DARK("Dark")
}

enum class SearchFilter(val label: String) {
    PDF("PDF"), IMAGES("Images"), DOWNLOADS("Downloads")
}

enum class CompressionLevel(val label: String, val deflater: Int) {
    FAST("Fast (larger file)", Deflater.BEST_SPEED),
    NORMAL("Normal (recommended)", Deflater.DEFAULT_COMPRESSION),
    BEST("Best (smaller file)", Deflater.BEST_COMPRESSION)
}

enum class OpKind { COPY, MOVE, COMPRESS, SHARE }

/** A multi-step operation waiting for the user to finish picking options. */
data class PendingOp(val kind: OpKind, val paths: List<String>)

data class AppSettings(
    val onboardingDone: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val sort: SortOrder = SortOrder.NAME,
    val showHidden: Boolean = false,
    val confirmDelete: Boolean = true
)
