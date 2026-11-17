package com.patbaumgartner.android.myapps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patbaumgartner.android.myapps.R
import com.patbaumgartner.android.myapps.ui.AppStatus
import com.patbaumgartner.android.myapps.ui.AppUiState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAppsScreen(
    apps: StateFlow<Map<String, AppUiState>>,
    onRefresh: () -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val snapshot by apps.collectAsState()
    val refreshing = snapshot.values.any { it.status == AppStatus.CHECKING }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.screen_my_apps),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.my_apps_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            items(snapshot.values.sortedBy { it.app.displayName }, key = { it.app.repo }) { state ->
                AppCard(state = state, onClick = { onOpenDetails(state.app.repo) })
            }
        }
    }
}

@Composable
fun AppCard(state: AppUiState, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.installed != null) {
                    com.patbaumgartner.android.myapps.ui.components.InstalledAppIcon(
                        packageId = state.app.packageId,
                        fallbackLetter = state.app.displayName.first().uppercaseChar().toString(),
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    com.patbaumgartner.android.myapps.ui.components.LetterIcon(
                        state.app.displayName.first().uppercaseChar().toString(),
                        Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.app.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(state.status)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = versionLine(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.status == AppStatus.CHECKING) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(Modifier.size(20.dp))
            }
            state.message?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun versionLine(state: AppUiState): String {
    val installed = state.installed?.let { "${it.versionName} (${it.versionCode})" } ?: stringResource(R.string.not_installed)
    val latest = state.release?.let { release ->
        // The snapshot release is titled "Snapshot" and the channel label is "Snapshot"
        // too — don't print the same word twice.
        if (release.name.equals(state.channel.label, ignoreCase = true)) {
            release.name
        } else {
            "${release.name} · ${state.channel.label}"
        }
    } ?: "—"
    return stringResource(R.string.versions_line, installed, latest)
}

@Composable
fun StatusChip(status: AppStatus) {
    val (label, container, content) = when (status) {
        AppStatus.UPDATE_AVAILABLE -> Triple(stringResource(R.string.status_update_available), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        AppStatus.NOT_INSTALLED -> Triple(stringResource(R.string.status_not_installed), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        AppStatus.UP_TO_DATE -> Triple(stringResource(R.string.status_up_to_date), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        AppStatus.UNCHANGED -> Triple(stringResource(R.string.status_unchanged), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        AppStatus.DOWNLOADING, AppStatus.UNINSTALLING, AppStatus.INSTALLING -> Triple(stringResource(R.string.status_working), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        AppStatus.INSTALL_FAILED, AppStatus.CHECK_FAILED -> Triple(stringResource(R.string.status_failed), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        AppStatus.CHECKING, AppStatus.IDLE -> Triple(stringResource(R.string.status_idle), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    androidx.compose.material3.AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    )
}

