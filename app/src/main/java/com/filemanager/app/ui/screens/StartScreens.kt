package com.filemanager.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.filemanager.app.LocalContainer
import com.filemanager.app.ui.components.PermissionPrompt
import com.filemanager.app.ui.components.PrimaryButton
import com.filemanager.app.util.StorageAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Screen 1 */
@Composable
fun SplashScreen(onDone: (onboardingDone: Boolean) -> Unit) {
    val container = LocalContainer.current
    LaunchedEffect(Unit) {
        val done = container.settings.settings.first().onboardingDone
        delay(1200)
        onDone(done)
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("File Manager", style = MaterialTheme.typography.headlineMedium)
        Text("Your files. Organised. Safe.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class OnboardingPage(val icon: ImageVector, val title: String, val body: String)

private val onboardingPages = listOf(
    OnboardingPage(
        Icons.Outlined.Folder, "Access all your files",
        "Browse, manage and organise your files, photos, videos and more in one place."
    ),
    OnboardingPage(
        Icons.Outlined.Lock, "Keep your data safe",
        "Your files stay on your device. This app does not send your files anywhere."
    ),
    OnboardingPage(
        Icons.Outlined.TouchApp, "Simple and powerful",
        "Quick actions, search and smart features help you get things done faster."
    )
)

/** Screens 2, 3 and 4 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { onboardingPages.size })
    val last = pager.currentPage == onboardingPages.lastIndex

    fun finish() {
        scope.launch {
            container.settings.setOnboardingDone()
            onFinished()
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${pager.currentPage + 1} of ${onboardingPages.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!last) TextButton(onClick = { finish() }) { Text("Skip") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val p = onboardingPages[page]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(160.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(p.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
                }
                Spacer(Modifier.height(32.dp))
                Text(p.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    p.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(onboardingPages.size) { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(if (i == pager.currentPage) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        PrimaryButton(
            text = if (last) "Get started" else "Next",
            onClick = {
                if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Wraps a screen so it only shows once the user has granted file access. */
@Composable
fun RequireStorageAccess(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(StorageAccess.isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = StorageAccess.isGranted(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = StorageAccess.isGranted(context) }

    if (granted) {
        content()
    } else {
        val useSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
            PermissionPrompt(
                isSettingsFlow = useSettings,
                onAllow = {
                    if (useSettings) context.startActivity(StorageAccess.settingsIntent(context))
                    else launcher.launch(StorageAccess.legacyPermissions)
                }
            )
        }
    }
}
