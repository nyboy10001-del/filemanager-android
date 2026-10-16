package com.filemanager.app.data

import android.webkit.MimeTypeMap
import java.io.File

object FileTypes {
    private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val videos = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v")
    private val audio = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "amr")
    private val documents = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "odt", "ods", "rtf", "md"
    )
    private val archives = setOf("zip", "rar", "7z", "tar", "gz")

    fun categoryOf(file: File): FileCategory {
        if (file.isDirectory) return FileCategory.FOLDER
        val ext = file.extension.lowercase()
        return when {
            ext in images -> FileCategory.IMAGES
            ext in videos -> FileCategory.VIDEOS
            ext in audio -> FileCategory.AUDIO
            ext in documents -> FileCategory.DOCUMENTS
            ext == "apk" -> FileCategory.APPS
            ext in archives -> FileCategory.ARCHIVES
            else -> FileCategory.OTHER
        }
    }

    fun mimeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

    fun typeLabel(file: File): String {
        if (file.isDirectory) return "Folder"
        val ext = file.extension
        return if (ext.isEmpty()) "File" else "${ext.uppercase()} file"
    }
}
