package com.filemanager.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.filemanager.app.LocalContainer
import com.filemanager.app.data.OpKind
import com.filemanager.app.data.PendingOp
import com.filemanager.app.ui.components.NavTab
import com.filemanager.app.ui.screens.BrowserActions
import com.filemanager.app.ui.screens.BrowserScreen
import com.filemanager.app.ui.screens.CompressScreen
import com.filemanager.app.ui.screens.CreateFolderScreen
import com.filemanager.app.ui.screens.DestinationScreen
import com.filemanager.app.ui.screens.DetailsScreen
import com.filemanager.app.ui.screens.HelpScreen
import com.filemanager.app.ui.screens.HomeScreen
import com.filemanager.app.ui.screens.OnboardingScreen
import com.filemanager.app.ui.screens.RecentScreen
import com.filemanager.app.ui.screens.SearchScreen
import com.filemanager.app.ui.screens.SettingsScreen
import com.filemanager.app.ui.screens.ShareScreen
import com.filemanager.app.ui.screens.SharedScreen
import com.filemanager.app.ui.screens.SplashScreen
import com.filemanager.app.ui.screens.StorageScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val STORAGE = "storage"
    const val SEARCH = "search"
    const val RECENT = "recent"
    const val SHARED = "shared"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val COMPRESS = "compress"
    const val SHARE = "share"
    const val BROWSER = "browser/{path}"
    const val DETAILS = "details/{path}"
    const val NEW_FOLDER = "newfolder/{path}"
    const val DESTINATION = "destination/{kind}"

    fun browser(path: String) = "browser/${Uri.encode(path)}"
    fun details(path: String) = "details/${Uri.encode(path)}"
    fun newFolder(path: String) = "newfolder/${Uri.encode(path)}"
    fun destination(kind: OpKind) = "destination/${kind.name}"
}

private fun NavController.openTab(tab: NavTab) {
    navigate(tab.route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNav() {
    val container = LocalContainer.current
    val nav = rememberNavController()

    val openTab: (NavTab) -> Unit = { nav.openTab(it) }
    val openFolder: (String) -> Unit = { nav.navigate(Routes.browser(it)) }
    val openDetails: (String) -> Unit = { nav.navigate(Routes.details(it)) }
    val startOp: (OpKind, List<String>) -> Unit = { kind, paths ->
        container.pending.value = PendingOp(kind, paths)
        nav.navigate(
            when (kind) {
                OpKind.COPY, OpKind.MOVE -> Routes.destination(kind)
                OpKind.COMPRESS -> Routes.COMPRESS
                OpKind.SHARE -> Routes.SHARE
            }
        )
    }
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen { onboarded ->
                nav.navigate(if (onboarded) Routes.HOME else Routes.ONBOARDING) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            }
        }
        composable(Routes.HOME) {
            HomeScreen(
                onSearch = { nav.navigate(Routes.SEARCH) },
                onOpenFolder = openFolder,
                onStorage = { nav.navigate(Routes.STORAGE) },
                onHelp = { nav.navigate(Routes.HELP) },
                onTab = openTab
            )
        }
        composable(Routes.STORAGE) { StorageScreen(onBack = back) }
        composable(Routes.SEARCH) { SearchScreen(onBack = back, onOpenFolder = openFolder) }
        composable(Routes.RECENT) {
            RecentScreen(onTab = openTab, onShowInFolder = openFolder, onDetails = openDetails, onOperation = startOp)
        }
        composable(Routes.SHARED) { SharedScreen(onTab = openTab) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onTab = openTab, onStorage = { nav.navigate(Routes.STORAGE) }, onHelp = { nav.navigate(Routes.HELP) })
        }
        composable(Routes.HELP) { HelpScreen(onBack = back) }
        composable(Routes.BROWSER) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            BrowserScreen(
                path = path,
                actions = BrowserActions(
                    onBack = back,
                    onOpenFolder = openFolder,
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onDetails = openDetails,
                    onNewFolder = { nav.navigate(Routes.newFolder(it)) },
                    onOperation = startOp,
                    onTab = openTab
                )
            )
        }
        composable(Routes.DETAILS) { entry ->
            DetailsScreen(path = entry.arguments?.getString("path").orEmpty(), onClose = back)
        }
        composable(Routes.NEW_FOLDER) { entry ->
            CreateFolderScreen(parentPath = entry.arguments?.getString("path").orEmpty(), onClose = back)
        }
        composable(Routes.DESTINATION) { entry ->
            val kind = runCatching { OpKind.valueOf(entry.arguments?.getString("kind").orEmpty()) }.getOrDefault(OpKind.COPY)
            DestinationScreen(kind = kind, onClose = back)
        }
        composable(Routes.COMPRESS) { CompressScreen(onClose = back) }
        composable(Routes.SHARE) { ShareScreen(onClose = back) }
    }
}
