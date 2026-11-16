package com.patbaumgartner.android.myapps.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.patbaumgartner.android.myapps.data.CheckOutcome
import com.patbaumgartner.android.myapps.data.PipelineResult
import com.patbaumgartner.android.myapps.data.UpdateChecker
import com.patbaumgartner.android.myapps.data.UpdatePipeline
import com.patbaumgartner.android.myapps.data.device.ApkArchiveReader
import com.patbaumgartner.android.myapps.data.device.DeviceInfo
import com.patbaumgartner.android.myapps.data.device.InstalledApp
import com.patbaumgartner.android.myapps.data.device.InstalledAppRepository
import com.patbaumgartner.android.myapps.data.github.GitHubClient
import com.patbaumgartner.android.myapps.data.install.InstallRepository
import com.patbaumgartner.android.myapps.data.install.InstallResultReceiver
import com.patbaumgartner.android.myapps.data.notify.Notifier
import com.patbaumgartner.android.myapps.data.settings.SettingsRepository
import com.patbaumgartner.android.myapps.data.work.UpdateCheckWorker
import com.patbaumgartner.android.myapps.domain.model.ApkSelector
import com.patbaumgartner.android.myapps.domain.model.Channel
import com.patbaumgartner.android.myapps.domain.model.ReleaseInfo
import com.patbaumgartner.android.myapps.domain.trust.ApprovedApp
import com.patbaumgartner.android.myapps.domain.trust.TrustRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppStatus {
    IDLE,
    CHECKING,
    NOT_INSTALLED,
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    UNCHANGED,
    DOWNLOADING,
    INSTALLING,
    INSTALL_FAILED,
    CHECK_FAILED,
}

data class AppUiState(
    val app: ApprovedApp,
    val installed: InstalledApp?,
    val channel: Channel,
    val status: AppStatus,
    val release: ReleaseInfo? = null,
    val selection: ApkSelector.Selection? = null,
    val fingerprint: String? = null,
    val progressBytes: Long = 0,
    val totalBytes: Long = -1,
    val message: String? = null,
)

data class DiscoveredRepo(val name: String, val description: String?)

class MyAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository(application)
    private val gitHub = GitHubClient(object : GitHubClient.EtagStore {
        override fun etagFor(path: String): String? = settings.etagFor(path)
        override fun save(path: String, etag: String?, body: String) = settings.setEtag(path, etag, body)
        override fun cachedBody(path: String): String? = settings.cachedBody(path)
    })
    private val installedApps = InstalledAppRepository(application)
    private val installer = InstallRepository(application)
    private val checker = UpdateChecker(gitHub, settings)
    private val pipeline = UpdatePipeline(
        application, gitHub, settings, installedApps, ApkArchiveReader(application), installer,
    )
    private val notifier = Notifier(application)

    private val _apps = MutableStateFlow(
        TrustRegistry.apps.associate { it.repo to AppUiState(it, installedApps.get(it.packageId), settings.channelFor(it.repo), AppStatus.IDLE) },
    )
    val apps: StateFlow<Map<String, AppUiState>> = _apps.asStateFlow()

    private val _discovered = MutableStateFlow<List<DiscoveredRepo>>(emptyList())
    val discovered: StateFlow<List<DiscoveredRepo>> = _discovered.asStateFlow()

    private val _globalMessage = MutableStateFlow<String?>(null)
    val globalMessage: StateFlow<String?> = _globalMessage.asStateFlow()

    private val _canInstallUnknown = MutableStateFlow(installer.canRequestPackageInstalls())
    val canInstallUnknown: StateFlow<Boolean> = _canInstallUnknown.asStateFlow()

    val backgroundChecksEnabled: StateFlow<Boolean> = MutableStateFlow(settings.backgroundChecksEnabled)
    val notificationsEnabled: StateFlow<Boolean> = MutableStateFlow(settings.notificationsEnabled)

    private val requestCodes = mutableMapOf<Int, String>()
    private var nextRequestCode = 1000

    init {
        InstallResultReceiver.listener = InstallResultReceiver.Listener { code, status, message ->
            if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // The system consent dialog is showing; the final status arrives later with the
                // same request code, so the mapping must stay until a terminal status.
                requestCodes[code]?.let { onInstallResult(it, status, message) }
            } else {
                val repo = requestCodes.remove(code) ?: return@Listener
                onInstallResult(repo, status, message)
            }
        }
        // Recover state after a process replacement (notably self-update).
        viewModelScope.launch {
            pipeline.reconcilePendingInstall()?.let { (repo, _) ->
                _globalMessage.value = "Updated to the latest version."
                refreshOne(repo)
            }
            refreshAll()
        }
    }

    fun refreshAll() {
        TrustRegistry.apps.forEach { app -> refreshOne(app.repo) }
        discoverRepos()
    }

    fun refreshOne(repo: String) {
        val state = _apps.value[repo] ?: return
        viewModelScope.launch {
            updateState(repo) { it.copy(status = AppStatus.CHECKING, message = null) }
            val installed = withContext(Dispatchers.IO) { installedApps.get(state.app.packageId) }
            when (val outcome = checker.check(state.app, installed)) {
                is CheckOutcome.UpdateAvailable -> updateState(repo) {
                    it.copy(
                        installed = installed,
                        status = AppStatus.UPDATE_AVAILABLE,
                        release = outcome.release,
                        selection = outcome.selection,
                        fingerprint = outcome.fingerprint,
                    )
                }

                is CheckOutcome.NotInstalled -> updateState(repo) {
                    it.copy(
                        installed = null,
                        status = AppStatus.NOT_INSTALLED,
                        release = outcome.release,
                        selection = outcome.selection,
                        fingerprint = outcome.fingerprint,
                    )
                }

                is CheckOutcome.UpToDate -> updateState(repo) {
                    it.copy(installed = installed, status = AppStatus.UP_TO_DATE, release = outcome.release, message = null)
                }

                is CheckOutcome.Unchanged -> updateState(repo) {
                    it.copy(installed = installed, status = AppStatus.UNCHANGED, release = outcome.release, message = null)
                }

                is CheckOutcome.Failed -> updateState(repo) {
                    it.copy(installed = installed, status = AppStatus.CHECK_FAILED, message = outcome.message)
                }
            }
        }
    }

    /** Downloads, verifies and hands the APK to the system installer. */
    fun install(repo: String) {
        val state = _apps.value[repo] ?: return
        val selection = state.selection ?: return
        val release = state.release ?: return
        viewModelScope.launch {
            updateState(repo) {
                it.copy(status = AppStatus.DOWNLOADING, message = null, progressBytes = 0, totalBytes = selection.apk.size)
            }
            when (val result = pipeline.downloadVerifyAndStage(state.app, release, selection) { done, total ->
                updateState(repo) { s -> s.copy(progressBytes = done, totalBytes = total) }
            }) {
                is PipelineResult.Failed -> updateState(repo) {
                    it.copy(status = AppStatus.INSTALL_FAILED, message = result.message)
                }

                is PipelineResult.NoUpdate -> {
                    settings.setLastOfferedFingerprint(repo, state.fingerprint.orEmpty())
                    updateState(repo) {
                        it.copy(status = AppStatus.UP_TO_DATE, message = "Already up to date (no downgrade).")
                    }
                }

                is PipelineResult.ReadyToInstall -> {
                    settings.setLastOfferedFingerprint(repo, state.fingerprint.orEmpty())
                    updateState(repo) { it.copy(status = AppStatus.INSTALLING) }
                    val code = nextRequestCode++
                    requestCodes[code] = repo
                    installer.install(result.apkFile, state.app.displayName, code).onFailure { error ->
                        requestCodes.remove(code)
                        updateState(repo) {
                            it.copy(status = AppStatus.INSTALL_FAILED, message = error.message)
                        }
                    }
                }
            }
        }
    }

    private fun onInstallResult(repo: String, status: Int, message: String?) {
        viewModelScope.launch {
            if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // Not terminal: keep INSTALLING while the system shows the consent UI.
                updateState(repo) { it.copy(status = AppStatus.INSTALLING, message = null) }
                return@launch
            }
            val text = InstallRepository.statusMessage(status, message)
            val success = status == android.content.pm.PackageInstaller.STATUS_SUCCESS
            updateState(repo) {
                it.copy(
                    status = if (success) AppStatus.UP_TO_DATE else AppStatus.INSTALL_FAILED,
                    message = text,
                )
            }
            if (success) {
                _apps.value[repo]?.let { state ->
                    if (settings.notificationsEnabled) notifier.notifyAppUpdated(state.app.displayName)
                }
                refreshOne(repo)
            }
        }
    }

    fun setChannel(repo: String, channel: Channel) {
        settings.setChannel(repo, channel)
        updateState(repo) { it.copy(channel = channel, status = AppStatus.IDLE, release = null, selection = null) }
        refreshOne(repo)
    }

    fun setBackgroundChecks(enabled: Boolean) {
        settings.backgroundChecksEnabled = enabled
        (backgroundChecksEnabled as MutableStateFlow).value = enabled
        UpdateCheckWorker.schedule(getApplication(), settings)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        settings.notificationsEnabled = enabled
        (notificationsEnabled as MutableStateFlow).value = enabled
    }

    fun refreshInstallPermission() {
        _canInstallUnknown.value = installer.canRequestPackageInstalls()
    }

    fun installerSettingsIntent() = installer.settingsIntent()

    fun notifier() = notifier

    fun deviceAbis() = DeviceInfo.supportedAbis

    private fun discoverRepos() {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { gitHub.listRepos(TrustRegistry.GITHUB_OWNER) }) {
                is com.patbaumgartner.android.myapps.data.github.GitHubResult.Ok -> _discovered.value =
                    result.value
                        .filter { it.name.endsWith("-android") }
                        .map { DiscoveredRepo(it.name, it.description) }

                else -> Unit // discovery is informational only; failures stay silent here
            }
        }
    }

    private fun updateState(repo: String, transform: (AppUiState) -> AppUiState) {
        _apps.update { map -> map[repo]?.let { map + (repo to transform(it)) } ?: map }
    }
}
