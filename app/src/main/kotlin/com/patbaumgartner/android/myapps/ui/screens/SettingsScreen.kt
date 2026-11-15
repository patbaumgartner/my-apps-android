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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.patbaumgartner.android.myapps.R
import com.patbaumgartner.android.myapps.ui.DiscoveredRepo
import com.patbaumgartner.android.myapps.ui.components.AuthorNote
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    backgroundChecks: StateFlow<Boolean>,
    wifiOnly: StateFlow<Boolean>,
    notifications: StateFlow<Boolean>,
    discovered: StateFlow<List<DiscoveredRepo>>,
    onBackgroundChecks: (Boolean) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
) {
    val resources = LocalResources.current
    var licensesShown by remember { mutableStateOf(false) }
    val background by backgroundChecks.collectAsState()
    val wifi by wifiOnly.collectAsState()
    val notif by notifications.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.screen_settings)) }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onCheckNow) { Text(stringResource(R.string.action_check_now)) }
            }
            item {
                SettingSwitch(
                    title = stringResource(R.string.settings_background_checks),
                    checked = background,
                    onChecked = onBackgroundChecks,
                )
            }
            item {
                SettingSwitch(
                    title = stringResource(R.string.settings_wifi_only),
                    checked = wifi,
                    enabled = background,
                    onChecked = onWifiOnly,
                )
            }
            item {
                SettingSwitch(
                    title = stringResource(R.string.settings_notifications),
                    checked = notif,
                    onChecked = onNotifications,
                )
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_discovered), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_discovered_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val unapproved = discovered.value
            if (unapproved.isNotEmpty()) {
                items@ for (repo in unapproved) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(repo.name, style = MaterialTheme.typography.bodyMedium)
                                repo.description?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                AuthorNote(onShowTypefaceLicense = { licensesShown = true })
            }
        }
    }

    if (licensesShown) {
        val licenseText = remember(resources) {
            resources.openRawResource(R.raw.manrope_license)
                .bufferedReader()
                .use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { licensesShown = false },
            title = { Text(stringResource(R.string.settings_open_source_licenses_title)) },
            text = {
                Text(
                    text = licenseText,
                    modifier = Modifier.height(360.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { licensesShown = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}
