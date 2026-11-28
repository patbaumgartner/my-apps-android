package com.patbaumgartner.android.myapps.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.patbaumgartner.android.myapps.R
import com.patbaumgartner.android.myapps.data.device.InstalledApp
import com.patbaumgartner.android.myapps.domain.model.Channel
import com.patbaumgartner.android.myapps.domain.model.ReleaseInfo
import com.patbaumgartner.android.myapps.domain.trust.TrustRegistry
import com.patbaumgartner.android.myapps.ui.screens.UpdatesScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualChannelSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun snapshotToStableShowsManualWarningAndNoInstallAction() {
        val app = TrustRegistry.apps.first { it.repo == "spaced-repeater-android" }
        val state = AppUiState(
            app = app,
            installed = InstalledApp(
                packageId = app.packageId,
                versionName = "snapshot-test",
                versionCode = 2_000_000_005L,
                signerCertSha256 = "test",
            ),
            channel = Channel.STABLE,
            status = AppStatus.MANUAL_ACTION_REQUIRED,
            release = ReleaseInfo(
                id = 42,
                tag = "v1.2.3",
                name = "v1.2.3",
                notes = "",
                prerelease = false,
                publishedAt = "",
                assets = emptyList(),
            ),
            requiresUninstall = true,
            message = context.getString(R.string.update_reinstall_hint),
        )
        var installRequested = false

        compose.setContent {
            UpdatesScreen(
                apps = MutableStateFlow(mapOf(app.repo to state)),
                onRefresh = {},
                onInstall = { installRequested = true },
                onOpenDetails = {},
            )
        }

        compose.onNodeWithText(context.getString(R.string.status_manual_action)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.update_reinstall_hint)).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.action_update)).assertCountEquals(0)
        compose.onAllNodesWithText(context.getString(R.string.action_install)).assertCountEquals(0)
        assertFalse("Manual-only state must not request an install or uninstall", installRequested)
    }
}
