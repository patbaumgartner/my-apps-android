package com.patbaumgartner.android.myapps.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patbaumgartner.android.myapps.R
import com.patbaumgartner.android.myapps.domain.model.Channel
import com.patbaumgartner.android.myapps.ui.AppStatus
import com.patbaumgartner.android.myapps.ui.AppUiState
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailsScreen(
    state: AppUiState?,
    canInstallUnknown: StateFlow<Boolean>,
    onInstall: () -> Unit,
    onChannel: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    val canInstall by canInstallUnknown.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.app?.displayName ?: stringResource(R.string.screen_details)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state == null) return@Scaffold
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(state.app.description, style = MaterialTheme.typography.bodyLarge)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.details_versions), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.versions_line,
                            state.installed?.let { "${it.versionName} (${it.versionCode})" } ?: stringResource(R.string.not_installed),
                            state.release?.name ?: "—",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.release?.let {
                        Text(
                            stringResource(R.string.details_channel_release, state.channel.label, it.tag, if (it.prerelease) "prerelease" else "stable"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(R.string.details_repo, state.app.owner, state.app.repo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.selection?.let {
                        Text(
                            stringResource(R.string.details_asset, it.apk.name, it.apk.size / 1_000_000),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.details_channel), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Channel.entries.forEach { channel ->
                        FilterChip(
                            selected = state.channel == channel,
                            onClick = { onChannel(channel) },
                            label = { Text(channel.label) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.details_channel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!canInstall) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.details_install_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onOpenInstallSettings) {
                            Text(stringResource(R.string.action_open_install_settings))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onInstall,
                    enabled = (state.status == AppStatus.UPDATE_AVAILABLE || state.status == AppStatus.NOT_INSTALLED) &&
                        canInstall,
                ) {
                    Text(
                        if (state.status == AppStatus.NOT_INSTALLED) {
                            stringResource(R.string.action_install)
                        } else {
                            stringResource(R.string.action_update)
                        },
                    )
                }
                androidx.compose.material3.OutlinedButton(onClick = onRefresh) {
                    Text(stringResource(R.string.action_check_now))
                }
            }

            if (state.status == AppStatus.DOWNLOADING) {
                val total = state.totalBytes
                val fraction = if (total > 0) state.progressBytes.toFloat() / total else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            if (state.status == AppStatus.INSTALLING) {
                Text(stringResource(R.string.status_installing), style = MaterialTheme.typography.bodySmall)
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            state.release?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.details_release_notes), style = MaterialTheme.typography.titleMedium)
                        Text(notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
