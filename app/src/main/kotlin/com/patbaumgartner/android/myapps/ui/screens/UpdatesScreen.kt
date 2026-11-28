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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
fun UpdatesScreen(
    apps: StateFlow<Map<String, AppUiState>>,
    onRefresh: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val snapshot by apps.collectAsState()
    val refreshing = snapshot.values.any { it.status == AppStatus.CHECKING }
    val actionable = snapshot.values.filter {
        it.status == AppStatus.UPDATE_AVAILABLE ||
            it.status == AppStatus.NOT_INSTALLED ||
            it.status == AppStatus.MANUAL_ACTION_REQUIRED
    }.sortedBy { it.app.displayName }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.screen_updates), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.updates_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (actionable.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.updates_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(actionable, key = { it.app.repo }) { state ->
                Card(onClick = { onOpenDetails(state.app.repo) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(state.app.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    versionLine(state),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.status == AppStatus.MANUAL_ACTION_REQUIRED) {
                                Text(
                                    stringResource(R.string.status_manual_action),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Button(
                                    onClick = { onInstall(state.app.repo) },
                                    enabled = state.status != AppStatus.DOWNLOADING && state.status != AppStatus.INSTALLING,
                                ) {
                                    Text(
                                        if (state.status == AppStatus.NOT_INSTALLED) {
                                            stringResource(R.string.action_install)
                                        } else {
                                            stringResource(R.string.action_update)
                                        },
                                    )
                                }
                            }
                        }
                        if (state.status == AppStatus.DOWNLOADING) {
                            Spacer(Modifier.height(8.dp))
                            val total = state.totalBytes
                            val fraction = if (total > 0) state.progressBytes.toFloat() / total else 0f
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                            Text(
                                stringResource(R.string.download_progress, state.progressBytes / 1_000_000, maxOf(total, 0) / 1_000_000),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.status == AppStatus.INSTALLING) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.status_installing),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.message?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
