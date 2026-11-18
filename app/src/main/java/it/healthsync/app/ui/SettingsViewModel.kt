package it.healthsync.app.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.healthsync.app.BuildConfig
import it.healthsync.app.data.SettingsRepository
import it.healthsync.app.data.SyncState
import it.healthsync.app.domain.BackfillState
import it.healthsync.app.domain.DEFAULT_AUTH_HEADER
import it.healthsync.app.domain.SettingsValidation
import it.healthsync.app.domain.validateWebhookSettings
import it.healthsync.app.health.HealthConnectGateway
import it.healthsync.app.health.HealthPermissions
import it.healthsync.app.sync.SyncOrigin
import it.healthsync.app.sync.SyncOutcome
import it.healthsync.app.sync.SyncRunner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HcAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE, UNKNOWN }

enum class BackgroundAccess { GRANTED, DENIED, UNSUPPORTED, UNKNOWN }

data class HealthUi(
    val availability: HcAvailability = HcAvailability.UNKNOWN,
    val grantedLabels: List<String> = emptyList(),
    val missingLabels: List<String> = emptyList(),
    val background: BackgroundAccess = BackgroundAccess.UNKNOWN,
    val history: BackgroundAccess = BackgroundAccess.UNKNOWN,
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsRepository(app)
    private val gateway = HealthConnectGateway(app)
    private val runner = SyncRunner(
        settings = settings,
        gateway = gateway,
        poster = it.healthsync.app.sync.WebhookClient(BuildConfig.VERSION_NAME),
        appVersion = BuildConfig.VERSION_NAME,
    )

    val appVersion: String = BuildConfig.VERSION_NAME
    val backfill: StateFlow<BackfillState?> = settings.backfill.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        settings.backfill.value,
    )
    val syncState: StateFlow<SyncState> = settings.syncState.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        settings.syncState.value,
    )

    var url = androidx.compose.runtime.mutableStateOf("")
    var headerName = androidx.compose.runtime.mutableStateOf(DEFAULT_AUTH_HEADER)
    var headerValue = androidx.compose.runtime.mutableStateOf("")
    var validation = androidx.compose.runtime.mutableStateOf(SettingsValidation(null, null, null))
    var health = androidx.compose.runtime.mutableStateOf(HealthUi())
    var syncing = androidx.compose.runtime.mutableStateOf(false)
    var banner = androidx.compose.runtime.mutableStateOf<String?>(null)

    init {
        val saved = settings.webhook()
        url.value = saved.url
        headerName.value = saved.headerName
        headerValue.value = saved.headerValue
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = gateway.sdkStatus()
            val availability = when (status) {
                HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
                else -> HcAvailability.UNAVAILABLE
            }
            if (availability != HcAvailability.AVAILABLE) {
                health.value = HealthUi(availability = availability)
                return@launch
            }
            val granted = gateway.grantedPermissions()
            val missing = HealthPermissions.metrics.filter { it.permission !in granted }
            if (granted.any { it in HealthPermissions.readPermissions }) {
                settings.rememberFirstGrant(java.time.Instant.now())
            }
            val background = featureState(gateway.backgroundSupported(), HealthPermissions.background in granted)
            val history = featureState(gateway.historySupported(), HealthPermissions.history in granted)
            health.value = HealthUi(
                availability = availability,
                grantedLabels = HealthPermissions.metrics.filter { it.permission in granted }.map { it.label },
                missingLabels = missing.map { it.label },
                background = background,
                history = history,
            )
        }
    }

    fun save() {
        val result = validateWebhookSettings(url.value, headerName.value, headerValue.value)
        validation.value = result
        if (!result.ok) return
        settings.saveWebhook(url.value, headerName.value, headerValue.value)
        headerName.value = settings.webhook().headerName
        banner.value = "Impostazioni salvate sul telefono."
    }

    fun importHistory() = runSync { runner.importHistory(SyncOrigin.FOREGROUND) }

    fun syncNow() = runSync { runner.sync(SyncOrigin.FOREGROUND) }

    private fun runSync(block: suspend () -> SyncOutcome) {
        val result = validateWebhookSettings(url.value, headerName.value, headerValue.value)
        validation.value = result
        if (!result.ok) {
            banner.value = "Controlla URL e chiave prima di sincronizzare."
            return
        }
        settings.saveWebhook(url.value, headerName.value, headerValue.value)
        headerName.value = settings.webhook().headerName
        viewModelScope.launch {
            syncing.value = true
            val outcome = block()
            syncing.value = false
            settings.reload()
            banner.value = when (outcome) {
                is SyncOutcome.Success -> "Sincronizzazione completata."
                is SyncOutcome.Failure -> outcome.message
                SyncOutcome.Busy -> "C'è già una sincronizzazione in corso."
            }
        }
    }

    fun consumeBanner() {
        banner.value = null
    }

    private fun featureState(supported: Boolean, granted: Boolean): BackgroundAccess {
        return when {
            !supported -> BackgroundAccess.UNSUPPORTED
            granted -> BackgroundAccess.GRANTED
            else -> BackgroundAccess.DENIED
        }
    }
}
