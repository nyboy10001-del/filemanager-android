package com.filemanager.app.data

import android.graphics.BitmapFactory
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.EnumMap
import java.util.PriorityQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileOpException(message: String) : IOException(message)

fun File.toItem(): FileItem {
    val dir = isDirectory
    return FileItem(
        path = absolutePath,
        name = name,
        isDirectory = dir,
        sizeBytes = if (dir) 0L else length(),
        modified = lastModified(),
        childCount = if (dir) (list()?.size ?: 0) else 0,
        category = FileTypes.categoryOf(this)
    )
}

/**
 * All file-system work lives here. Every public suspend function runs on Dispatchers.IO.
 * Mutations return Result so the UI can show plain-language errors, and emit [changes]
 * so any visible list can refresh itself.
 */
class FileRepository {
    val root: File = Environment.getExternalStorageDirectory()
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun publicDir(type: String): File = Environment.getExternalStoragePublicDirectory(type)

    val quickLocations: List<Pair<String, File>>
        get() = listOf(
            "Internal storage" to root,
            "Documents" to publicDir(Environment.DIRECTORY_DOCUMENTS),
            "Downloads" to publicDir(Environment.DIRECTORY_DOWNLOADS),
            "Pictures" to publicDir(Environment.DIRECTORY_PICTURES),
            "Music" to publicDir(Environment.DIRECTORY_MUSIC),
            "DCIM" to publicDir(Environment.DIRECTORY_DCIM),
            "Movies" to publicDir(Environment.DIRECTORY_MOVIES)
        )

    fun storageStats(): StorageStats {
        val sf = StatFs(root.path)
        val total = sf.totalBytes
        return StorageStats(total, total - sf.availableBytes)
    }

    // ---------- Reading ----------

    suspend fun list(path: String, sort: SortOrder, showHidden: Boolean): List<FileItem> =
        withContext(Dispatchers.IO) {
            val children = File(path).listFiles()
                ?: throw FileOpException("This folder can't be opened.")
            children.asSequence()
                .filter { showHidden || !it.isHidden }
                .map { it.toItem() }
                .sortedWith(comparator(sort))
                .toList()
        }

    private fun comparator(sort: SortOrder): Comparator<FileItem> {
        val secondary: Comparator<FileItem> = when (sort) {
            SortOrder.NAME -> compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortOrder.DATE -> compareByDescending<FileItem> { it.modified }
            SortOrder.SIZE -> compareByDescending<FileItem> { it.sizeBytes }
        }
        return compareBy<FileItem> { !it.isDirectory }.then(secondary)
    }

    private fun isAndroidDir(dir: File) =
        dir.name == "Android" && dir.parentFile?.absolutePath == root.absolutePath

    suspend fun folderSummary(dir: File): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val count = dir.list()?.size ?: 0
        var size = 0L
        for (f in dir.walkTopDown().onEnter { it.canRead() }) {
            ensureActive()
            if (f.isFile) size += f.length()
        }
        count to size
    }

    suspend fun breakdown(): Map<FileCategory, Long> = withContext(Dispatchers.IO) {
        val totals = EnumMap<FileCategory, Long>(FileCategory::class.java)
        for (f in root.walkTopDown().onEnter { it.canRead() && !isAndroidDir(it) }) {
            ensureActive()
            if (!f.isFile) continue
            val cat = FileTypes.categoryOf(f)
            totals[cat] = (totals[cat] ?: 0L) + f.length()
        }
        totals
    }

    suspend fun search(query: String, filter: SearchFilter?, showHidden: Boolean): List<FileItem> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            val start = if (filter == SearchFilter.DOWNLOADS)
                publicDir(Environment.DIRECTORY_DOWNLOADS) else root
            val out = ArrayList<FileItem>()
            if (!start.exists()) return@withContext out
            val walk = start.walkTopDown().onEnter { dir ->
                dir.canRead() && (showHidden || dir == start || !dir.isHidden) && !isAndroidDir(dir)
            }
            for (f in walk) {
                ensureActive()
                if (f == start) continue
                if (!showHidden && f.isHidden) continue
                if (q.isNotEmpty() && !f.name.contains(q, ignoreCase = true)) continue
                when (filter) {
                    SearchFilter.PDF ->
                        if (!(f.isFile && f.extension.equals("pdf", ignoreCase = true))) continue
                    SearchFilter.IMAGES ->
                        if (FileTypes.categoryOf(f) != FileCategory.IMAGES) continue
                    else -> Unit
                }
                out.add(f.toItem())
                if (out.size >= MAX_SEARCH_RESULTS) break
            }
            out
        }

    suspend fun recent(category: FileCategory?, limit: Int = 60): List<FileItem> =
        withContext(Dispatchers.IO) {
            newest(listOf(root), limit) { category == null || FileTypes.categoryOf(it) == category }
        }

    /** Files that arrived from other people or apps: Downloads and Bluetooth. */
    suspend fun received(limit: Int = 60): List<FileItem> = withContext(Dispatchers.IO) {
        newest(
            listOf(publicDir(Environment.DIRECTORY_DOWNLOADS), File(root, "Bluetooth")),
            limit
        ) { true }
    }

    private fun CoroutineScope.newest(
        starts: List<File>,
        limit: Int,
        accept: (File) -> Boolean
    ): List<FileItem> {
        val queue = PriorityQueue<Pair<Long, File>>(limit + 1, compareBy<Pair<Long, File>> { it.first })
        for (start in starts) {
            if (!start.isDirectory) continue
            val walk = start.walkTopDown().onEnter { it.canRead() && !it.isHidden && !isAndroidDir(it) }
            for (f in walk) {
                ensureActive()
                if (!f.isFile || f.isHidden || !accept(f)) continue
                queue.add(f.lastModified() to f)
                if (queue.size > limit) queue.poll()
            }
        }
        return queue.sortedByDescending { it.first }.map { it.second.toItem() }
    }

    suspend fun details(path: String): FileDetails = withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.exists()) throw FileNotFoundException("This item no longer exists.")
        val item = f.toItem()
        val total = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
        var dims: String? = null
        if (item.category == FileCategory.IMAGES) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            if (o.outWidth > 0) dims = "${o.outWidth} × ${o.outHeight} pixels"
        }
        FileDetails(item, FileTypes.typeLabel(f), f.parent.orEmpty(), total, dims)
    }

    // ---------- Validation ----------

    fun validateName(name: String, parent: File, ignore: File? = null): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Enter a name"
            n == "." || n == ".." -> "Name can't be . or .."
            n.any { it in "/\\:*?\"<>|\u0000" } -> "Name must not contain / \\ : * ? \" < > |"
            n.length > 255 -> "Name must be 255 characters or fewer"
            File(parent, n).let { it.exists() && it != ignore } ->
                "A file or folder called '$n' already exists here"
            else -> null
        }
    }

    // ---------- Mutations ----------

    private suspend fun <T> op(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching(block) }
            .also { if (it.isSuccess) changes.tryEmit(Unit) }

    suspend fun createFolder(parentPath: String, name: String): Result<File> = op {
        val parent = File(parentPath)
        validateName(name, parent)?.let { throw FileOpException(it) }
        val dir = File(parent, name.trim())
        if (!dir.mkdirs()) throw FileOpException("Couldn't create the folder. Check you have permission.")
        dir
    }

    suspend fun rename(path: String, newName: String): Result<File> = op {
        val src = File(path)
        val parent = src.parentFile ?: throw FileOpException("This item can't be renamed.")
        validateName(newName, parent, ignore = src)?.let { throw FileOpException(it) }
        val target = File(parent, newName.trim())
        if (!src.renameTo(target)) throw FileOpException("Couldn't rename this item.")
        target
    }

    suspend fun delete(paths: List<String>): Result<Int> = op {
        var failed = 0
        paths.forEach { if (!File(it).deleteRecursively()) failed++ }
        if (failed > 0) throw FileOpException("$failed ${if (failed == 1) "item" else "items"} couldn't be deleted.")
        paths.size
    }

    suspend fun copy(paths: List<String>, destPath: String): Result<Int> = op {
        val dest = File(destPath)
        paths.forEach { p ->
            val src = File(p)
            guardNotInside(src, dest, "copy")
            src.copyRecursively(uniqueTarget(dest, src.name), overwrite = false) { _, e -> throw e }
        }
        paths.size
    }

    suspend fun move(paths: List<String>, destPath: String): Result<Int> = op {
        val dest = File(destPath)
        paths.forEach { p ->
            val src = File(p)
            if (src.parentFile?.canonicalPath == dest.canonicalPath)
                throw FileOpException("'${src.name}' is already in this folder.")
            guardNotInside(src, dest, "move")
            val target = uniqueTarget(dest, src.name)
            if (!src.renameTo(target)) {
                src.copyRecursively(target, overwrite = false) { _, e -> throw e }
                if (!src.deleteRecursively()) throw FileOpException("Copied '${src.name}' but couldn't remove the original.")
            }
        }
        paths.size
    }

    suspend fun compress(paths: List<String>, archiveName: String, level: CompressionLevel): Result<File> = op {
        val destDir = File(paths.first()).parentFile ?: throw FileOpException("Can't create an archive here.")
        val base = archiveName.trim().removeSuffix(".zip").removeSuffix(".ZIP")
        val fileName = "$base.zip"
        validateName(fileName, destDir)?.let { throw FileOpException(it) }
        val target = File(destDir, fileName)
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use { zip ->
                zip.setLevel(level.deflater)
                paths.forEach { addToZip(zip, File(it), File(it).name) }
            }
        } catch (e: Exception) {
            target.delete()
            throw FileOpException("Couldn't create the archive: ${e.message ?: "unknown error"}")
        }
        target
    }

    private fun addToZip(zip: ZipOutputStream, file: File, entryName: String) {
        if (file.isDirectory) {
            val children = file.listFiles().orEmpty()
            if (children.isEmpty()) {
                zip.putNextEntry(ZipEntry("$entryName/"))
                zip.closeEntry()
            } else {
                children.forEach { addToZip(zip, it, "$entryName/${it.name}") }
            }
        } else {
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun guardNotInside(src: File, dest: File, verb: String) {
        if (!src.isDirectory) return
        val s = src.canonicalPath
        val d = dest.canonicalPath
        if (d == s || d.startsWith(s + File.separator))
            throw FileOpException("You can't $verb a folder into itself.")
    }

    private fun uniqueTarget(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val ext = first.extension
        val base = first.nameWithoutExtension
        var i = 1
        while (true) {
            val candidate = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    companion object {
        private const val MAX_SEARCH_RESULTS = 200
    }
}
