package com.patbaumgartner.android.myapps.data

import com.patbaumgartner.android.myapps.data.device.ApkArchiveReader
import com.patbaumgartner.android.myapps.data.device.DeviceInfo
import com.patbaumgartner.android.myapps.data.device.InstalledApp
import com.patbaumgartner.android.myapps.data.device.InstalledAppRepository
import com.patbaumgartner.android.myapps.data.github.GitHubClient
import com.patbaumgartner.android.myapps.data.github.GitHubResult
import com.patbaumgartner.android.myapps.data.install.InstallRepository
import com.patbaumgartner.android.myapps.data.settings.SettingsRepository
import com.patbaumgartner.android.myapps.domain.github.ReleaseParser
import com.patbaumgartner.android.myapps.domain.model.ApkSelector
import com.patbaumgartner.android.myapps.domain.model.Channel
import com.patbaumgartner.android.myapps.domain.model.ReleaseInfo
import com.patbaumgartner.android.myapps.domain.model.UpdateDecision
import com.patbaumgartner.android.myapps.domain.trust.ApprovedApp
import com.patbaumgartner.android.myapps.domain.verify.ApkVerifier
import com.patbaumgartner.android.myapps.domain.verify.Checksums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Outcome of a single app update check. */
sealed interface CheckOutcome {
    data class UpdateAvailable(
        val release: ReleaseInfo,
        val selection: ApkSelector.Selection,
        val fingerprint: String,
        /** True when this offer is the Snapshot→Stable channel switch (uninstall + reinstall). */
        val requiresUninstall: Boolean = false,
    ) : CheckOutcome

    data class NotInstalled(val release: ReleaseInfo, val selection: ApkSelector.Selection, val fingerprint: String) :
        CheckOutcome

    data class UpToDate(val release: ReleaseInfo?) : CheckOutcome
    data class Unchanged(val release: ReleaseInfo, val fingerprint: String) : CheckOutcome
    data class Failed(val message: String) : CheckOutcome
}

/**
 * Checks one approved app on its configured channel and decides whether an update should
 * be offered. Network I/O runs on [Dispatchers.IO] via the injected client.
 */
class UpdateChecker(
    private val gitHub: GitHubClient,
    private val settings: SettingsRepository,
) {
    suspend fun check(app: ApprovedApp, installed: InstalledApp?): CheckOutcome = withContext(Dispatchers.IO) {
        val channel = settings.channelFor(app.repo)
        val tag = if (channel == Channel.SNAPSHOT) SNAPSHOT_TAG else null
        val gh = if (tag == null) {
            gitHub.latestRelease(app.owner, app.repo)
        } else {
            gitHub.releaseByTag(app.owner, app.repo, tag)
        }
        val release = when (gh) {
            is GitHubResult.Ok -> ReleaseParser.parse(gh.value, channel).getOrElse {
                return@withContext CheckOutcome.Failed(it.message ?: "release rejected")
            }

            is GitHubResult.NotModified -> parseCached(gh.cachedBody, channel)
                ?: return@withContext CheckOutcome.Failed("cached release unreadable")

            is GitHubResult.HttpError, is GitHubResult.NetworkError -> {
                // Rate limits (60 req/h unauthenticated, shared per IP) and offline must not
                // hide the last known-good release: fall back to the ETag-cached body.
                val cached = gitHub.cachedRelease(app.owner, app.repo, tag)
                if (cached != null) {
                    parseCached(cached, channel)
                        ?: return@withContext CheckOutcome.Failed("cached release unreadable")
                } else {
                    val message = when (gh) {
                        is GitHubResult.HttpError ->
                            if (gh.rateLimited) RATE_LIMIT_MESSAGE else "GitHub returned HTTP ${gh.code}"

                        is GitHubResult.NetworkError -> gh.message
                        else -> "GitHub request failed"
                    }
                    return@withContext CheckOutcome.Failed(message)
                }
            }
        }
        if (release.assets.isEmpty()) return@withContext CheckOutcome.Failed("release ${release.tag} has no assets")

        val selection = ApkSelector.select(release.assets, app, DeviceInfo.supportedAbis).getOrElse {
            return@withContext CheckOutcome.Failed(it.message ?: "no installable APK in release")
        }
        val fingerprint = UpdateDecision.candidateFingerprint(release, selection.apk.id)
        settings.setLastCheckAt(app.repo, System.currentTimeMillis())
        // Pre-download gate: the tag-derived versionCode (null for the `snapshot` tag,
        // where only the fingerprint rule applies) decides whether the candidate is even
        // worth offering. The authoritative comparison still uses the APK's own
        // versionCode in [UpdatePipeline.downloadVerifyAndStage].
        val candidateVersionCode = UpdateDecision.versionCodeFromTag(release.tag)
        // A stable candidate over an installed snapshot is the channel switch back to
        // Stable: it is offered even though its versionCode is lower, and the install is
        // later applied as uninstall + reinstall (Android cannot downgrade in place).
        val requiresUninstall = candidateVersionCode != null &&
            UpdateDecision.isDowngradeInstall(installed?.versionCode, candidateVersionCode)
        when (UpdateDecision.shouldOffer(installed?.versionCode, settings.lastOfferedFingerprint(app.repo), fingerprint, candidateVersionCode)) {
            UpdateDecision.Offer.SKIP_UNCHANGED -> CheckOutcome.Unchanged(release, fingerprint)
            UpdateDecision.Offer.NO_UPDATE -> CheckOutcome.UpToDate(release)
            UpdateDecision.Offer.YES -> if (installed == null) {
                CheckOutcome.NotInstalled(release, selection, fingerprint)
            } else {
                CheckOutcome.UpdateAvailable(release, selection, fingerprint, requiresUninstall)
            }
        }
    }

    private fun parseCached(body: String, channel: Channel): ReleaseInfo? = runCatching {
        ReleaseParser.parse(gitHub.parseRelease(body), channel).getOrThrow()
    }.getOrNull()

    companion object {
        const val SNAPSHOT_TAG = "snapshot"
        const val RATE_LIMIT_MESSAGE = "GitHub API rate limit reached — try again later"
    }
}

/** Result of downloading, verifying and handing an APK to the system installer. */
sealed interface PipelineResult {
    data class ReadyToInstall(
        val apkFile: File,
        val archiveVersionCode: Long,
        /** True for the Snapshot→Stable switch: uninstall first, then install [apkFile]. */
        val requiresUninstall: Boolean = false,
    ) : PipelineResult
    data class NoUpdate(val archiveVersionCode: Long, val installedVersionCode: Long) : PipelineResult
    data class Failed(val message: String) : PipelineResult
}

/**
 * Downloads the selected APK, verifies it end-to-end (checksum, package id, signer,
 * minSdk, versionCode) and — when it is a genuine update — stages it for the system
 * installer. Never installs silently.
 */
class UpdatePipeline(
    private val context: android.content.Context,
    private val gitHub: GitHubClient,
    private val settings: SettingsRepository,
    private val installedApps: InstalledAppRepository,
    private val archiveReader: ApkArchiveReader,
    private val installer: InstallRepository,
) {
    suspend fun downloadVerifyAndStage(
        app: ApprovedApp,
        release: ReleaseInfo,
        selection: ApkSelector.Selection,
        onProgress: (Long, Long) -> Unit,
    ): PipelineResult = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "apks").apply { mkdirs() }

        // 1. Fetch the checksum manifest and resolve the expected digest for our APK.
        val checksumsFile = File(cacheDir, "${app.repo}-SHA256SUMS")
        gitHub.download(selection.checksumsAsset.downloadUrl, checksumsFile) { _, _ -> }
            .getOrElse { return@withContext PipelineResult.Failed("could not download SHA256SUMS: ${it.message}") }
        val expectedSha256 = ApkSelector.expectedSha256(checksumsFile.readText(), selection.apk.name)
            ?: return@withContext PipelineResult.Failed("SHA256SUMS has no entry for ${selection.apk.name}")

        // 2. Download the APK with progress.
        val apkFile = File(cacheDir, selection.apk.name)
        gitHub.download(selection.apk.downloadUrl, apkFile, onProgress)
            .getOrElse { return@withContext PipelineResult.Failed("download failed: ${it.message}") }

        // 3. Integrity: SHA-256 of the file must match the manifest entry.
        val actualSha256 = Checksums.sha256Hex(apkFile)

        // 4. Archive metadata: package id, versionCode, minSdk, signer certificate.
        val meta = archiveReader.read(apkFile)
            ?: return@withContext PipelineResult.Failed("could not read APK archive")

        ApkVerifier.verify(
            app = app,
            meta = meta,
            actualSha256 = actualSha256,
            expectedSha256 = expectedSha256,
            deviceSdkInt = DeviceInfo.sdkInt,
            deviceAbis = DeviceInfo.supportedAbis,
        ).getOrElse { return@withContext PipelineResult.Failed(it.message ?: "verification failed") }

        // 5. Version gate: use the APK's own versionCode; never downgrade — except the
        // Snapshot→Stable channel switch, which is applied as uninstall + reinstall.
        val installed = installedApps.get(app.packageId)
        val stableRelease = UpdateDecision.versionCodeFromTag(release.tag) != null
        when (UpdateDecision.afterDownload(installed?.versionCode, meta.versionCode, stableRelease)) {
            UpdateDecision.Install.NO_UPDATE -> PipelineResult.NoUpdate(meta.versionCode, installed!!.versionCode)
            UpdateDecision.Install.PROCEED -> {
                val requiresUninstall = UpdateDecision.isDowngradeInstall(installed?.versionCode, meta.versionCode)
                settings.pendingInstall = SettingsRepository.PendingInstall(
                    repo = app.repo,
                    expectedVersionCode = meta.versionCode,
                    apkFileName = apkFile.name,
                    requiresUninstall = requiresUninstall,
                )
                PipelineResult.ReadyToInstall(apkFile, meta.versionCode, requiresUninstall)
            }
        }
    }

    /** Hands a verified APK to the system installer (user approves in the system UI). */
    fun requestInstall(app: ApprovedApp, apkFile: File, requestCode: Int): Result<Unit> {
        settings.pendingInstall = SettingsRepository.PendingInstall(
            repo = app.repo,
            expectedVersionCode = settings.pendingInstall?.expectedVersionCode ?: -1L,
            apkFileName = apkFile.name,
            requiresUninstall = settings.pendingInstall?.requiresUninstall ?: false,
        )
        return installer.install(apkFile, app.displayName, requestCode)
    }

    /**
     * Reconciles a pending install after a process restart (e.g. self-update): if the
     * expected versionCode is now installed, the update succeeded and the marker clears.
     */
    fun reconcilePendingInstall(): Pair<String, Long>? {
        val pending = settings.pendingInstall ?: return null
        val installed = installedApps.get(com.patbaumgartner.android.myapps.domain.trust.TrustRegistry.findByRepo(pending.repo)?.packageId ?: return null)
        return if (installed != null && installed.versionCode >= pending.expectedVersionCode) {
            settings.pendingInstall = null
            pending.repo to installed.versionCode
        } else {
            null
        }
    }
}
