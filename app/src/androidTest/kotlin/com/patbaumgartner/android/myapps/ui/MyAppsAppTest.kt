package com.patbaumgartner.android.myapps.ui

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.patbaumgartner.android.myapps.R
import com.patbaumgartner.android.myapps.data.notify.Notifier
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented end-to-end UI tests run against the real activity on a device or
 * emulator (CI runs them via `connectedDebugAndroidTest`).
 *
 * Screen titles and bottom-navigation labels share the same string resources
 * (e.g. `screen_my_apps` is both the top-bar title and the nav label), so a
 * label can match several nodes and the single-node [onNodeWithText] fails on
 * multiple matches. Duplicated labels are asserted with [onAllNodesWithText] +
 * [assertAny] (the nav entry is the clickable one); unique strings keep the
 * strict single-node assertions.
 */
@RunWith(AndroidJUnit4::class)
class MyAppsAppTest {

    // The activity asks for POST_NOTIFICATIONS on API 33+; grant it so no system
    // dialog overlays the UI under test.
    @get:Rule
    val permission = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun str(res: Int) = compose.activity.getString(res)

    @Test
    fun myAppsScreenShowsApprovedAppsList() {
        // Unique to the My Apps screen: top-bar title + subtitle.
        compose.onNodeWithText(str(R.string.my_apps_subtitle)).assertIsDisplayed()
        // "My Apps" also exists as the bottom-nav entry (clickable).
        compose.onAllNodesWithText(str(R.string.screen_my_apps)).assertAny(hasClickAction())
    }

    @Test
    fun bottomNavigationOffersMyAppsUpdatesAndSettings() {
        for (res in listOf(R.string.screen_my_apps, R.string.screen_updates, R.string.screen_settings)) {
            compose.onAllNodesWithText(str(res)).assertAny(hasClickAction())
        }
    }

    @Test
    fun navigatingToSettingsShowsManualUpdateCheck() {
        // On the My Apps screen only the bottom-nav label matches, so it is the
        // single node to click.
        compose.onAllNodesWithText(str(R.string.screen_settings))[0].performClick()
        // Unique to the Settings screen: the manual update button.
        compose.onNodeWithText(str(R.string.action_check_now))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun settingsScreenHasNoWifiOnlyToggle() {
        compose.onAllNodesWithText(str(R.string.screen_settings))[0].performClick()
        // The "Only on unmetered Wi-Fi" toggle was removed; background checks now
        // always run on unmetered networks.
        compose.onAllNodesWithText("Only on unmetered Wi-Fi").assertCountEquals(0)
        // The remaining update-check controls are still there.
        compose.onNodeWithText(str(R.string.settings_background_checks)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.settings_notifications)).assertIsDisplayed()
    }

    @Test
    fun notificationsChannelExistsAndPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = compose.activity.getSystemService(NotificationManager::class.java)
            assertNotNull(manager.getNotificationChannel(Notifier.CHANNEL_UPDATES))
        }
        // POST_NOTIFICATIONS is granted via GrantPermissionRule above.
        assertTrue(Notifier(compose.activity).notificationsAllowed())
    }
}
