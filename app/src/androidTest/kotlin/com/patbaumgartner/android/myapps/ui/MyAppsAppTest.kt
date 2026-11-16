package com.patbaumgartner.android.myapps.ui

import android.Manifest
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.patbaumgartner.android.myapps.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented end-to-end UI tests run against the real activity on a device or
 * emulator (CI runs them via `connectedDebugAndroidTest`).
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
        compose.onNodeWithText(str(R.string.screen_my_apps)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.my_apps_subtitle)).assertIsDisplayed()
    }

    @Test
    fun bottomNavigationOffersMyAppsUpdatesAndSettings() {
        compose.onNodeWithText(str(R.string.screen_my_apps)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.screen_updates)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.screen_settings)).assertIsDisplayed()
    }

    @Test
    fun navigatingToSettingsShowsManualUpdateCheck() {
        compose.onNodeWithText(str(R.string.screen_settings)).performClick()
        compose.onNodeWithText(str(R.string.screen_settings)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_check_now))
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
