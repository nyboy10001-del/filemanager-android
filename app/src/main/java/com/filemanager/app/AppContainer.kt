package com.filemanager.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import com.filemanager.app.data.FileRepository
import com.filemanager.app.data.PendingOp
import com.filemanager.app.data.SettingsStore
import com.filemanager.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container: one instance for the whole process. */
class AppContainer(context: Context) {
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "filemanager.db").build()

    val settings = SettingsStore(context)
    val files = FileRepository()
    val searchDao = db.searchDao()
    val shareDao = db.shareDao()

    /** The copy / move / compress / share the user is currently setting up. */
    val pending = MutableStateFlow<PendingOp?>(null)

    /** One-line messages shown in the app-wide snackbar. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Lets long file operations finish even if the user leaves the screen. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun <T> background(block: suspend () -> T): Deferred<T> = appScope.async { block() }
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
