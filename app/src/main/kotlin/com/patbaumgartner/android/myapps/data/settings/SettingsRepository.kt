package com.patbaumgartner.android.myapps.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.patbaumgartner.android.myapps.domain.model.Channel
import com.patbaumgartner.android.myapps.domain.trust.TrustRegistry

/**
 * Thin SharedPreferences wrapper. All keys are prefixed; nothing here is security-sensitive
 * (the trust registry is bundled code, never preferences).
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun channelFor(repo: String): Channel =
        if (prefs.getBoolean("channel.$repo.snapshot", false)) Channel.SNAPSHOT else Channel.STABLE

    fun setChannel(repo: String, channel: Channel) {
        prefs.edit { putBoolean("channel.$repo.snapshot", channel == Channel.SNAPSHOT) }
    }

    fun lastOfferedFingerprint(repo: String): String? = prefs.getString("offered.$repo", null)

    fun setLastOfferedFingerprint(repo: String, fingerprint: String) {
        prefs.edit { putString("offered.$repo", fingerprint) }
    }

    fun lastCheckAt(repo: String): Long = prefs.getLong("checked.$repo", 0L)

    fun setLastCheckAt(repo: String, timestamp: Long) {
        prefs.edit { putLong("checked.$repo", timestamp) }
    }

    var backgroundChecksEnabled: Boolean
        get() = prefs.getBoolean("bg.enabled", false)
        set(value) = prefs.edit { putBoolean("bg.enabled", value) }

    var backgroundWifiOnly: Boolean
        get() = prefs.getBoolean("bg.wifiOnly", true)
        set(value) = prefs.edit { putBoolean("bg.wifiOnly", value) }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications.enabled", true)
        set(value) = prefs.edit { putBoolean("notifications.enabled", value) }

    /** ETag cache for GitHub API GETs, keyed by request path. */
    fun etagFor(path: String): String? = prefs.getString("etag.$path", null)

    fun setEtag(path: String, etag: String?, body: String) {
        prefs.edit {
            putString("etag.$path", etag)
            putString("etagbody.$path", body)
        }
    }

    fun cachedBody(path: String): String? = prefs.getString("etagbody.$path", null)

    /**
     * A pending install that survives process death (the system kills the app while its
     * own update installs). Cleared once the expected version is observed installed.
     */
    data class PendingInstall(
        val repo: String,
        val expectedVersionCode: Long,
        val apkFileName: String,
    )

    var pendingInstall: PendingInstall?
        get() = prefs.getString("pending.repo", null)?.let { repo ->
            PendingInstall(
                repo = repo,
                expectedVersionCode = prefs.getLong("pending.versionCode", -1L),
                apkFileName = prefs.getString("pending.apk", "").orEmpty(),
            )
        }
        set(value) {
            prefs.edit {
                if (value == null) {
                    remove("pending.repo").remove("pending.versionCode").remove("pending.apk")
                } else {
                    putString("pending.repo", value.repo)
                    putLong("pending.versionCode", value.expectedVersionCode)
                    putString("pending.apk", value.apkFileName)
                }
            }
        }

    fun allRepos(): List<String> = TrustRegistry.apps.map { it.repo }

    companion object {
        const val PREFS_NAME = "myapps.settings"
    }
}
