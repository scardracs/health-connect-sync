package it.healthsync.app.ui

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.healthsync.app.R
import it.healthsync.app.data.SyncStatus
import it.healthsync.app.domain.ExternalScreens
import it.healthsync.app.domain.FieldError
import it.healthsync.app.domain.ItalianDates
import it.healthsync.app.domain.SendPlan
import it.healthsync.app.domain.calorieOnlySourceHint
import it.healthsync.app.health.HealthPermissions
import java.time.ZoneId

private enum class Page { Home, Settings }

@Composable
fun SaluteApp(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var page by remember { mutableStateOf(Page.Home) }
    var ignoringBattery by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    val readPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }
    val historyPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
                val power = context.getSystemService(PowerManager::class.java)
                ignoringBattery = power?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel.banner.value) {
        val text = viewModel.banner.value ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.consumeBanner()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (page) {
            Page.Home -> HomePage(
                viewModel = viewModel,
                zone = zone,
                ignoringBattery = ignoringBattery,
                onOpenSettings = { page = Page.Settings },
                onBattery = { requestBatteryExemption(context) },
                modifier = Modifier.padding(padding),
            )
            Page.Settings -> SettingsPage(
                viewModel = viewModel,
                onBack = { page = Page.Home },
                onGrantRead = {
                    try {
                        readPermissionLauncher.launch(HealthPermissions.readPermissions)
                    } catch (_: Exception) {
                        viewModel.banner.value = context.getString(R.string.grant_read_failed)
                    }
                },
                onGrantBackground = {
                    try {
                        backgroundPermissionLauncher.launch(setOf(HealthPermissions.background))
                    } catch (_: Exception) {
                        viewModel.banner.value = context.getString(R.string.grant_background_failed)
                    }
                },
                onGrantHistory = {
                    try {
                        historyPermissionLauncher.launch(setOf(HealthPermissions.history))
                    } catch (_: Exception) {
                        viewModel.banner.value = context.getString(R.string.grant_history_failed)
                    }
                },
                onOpenHealthConnect = { openHealthConnect(context) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HomePage(
    viewModel: SettingsViewModel,
    zone: ZoneId,
    ignoringBattery: Boolean,
    onOpenSettings: () -> Unit,
    onBattery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val health = viewModel.health.value
    val webhookMissing = viewModel.url.value.isBlank() || viewModel.headerValue.value.isBlank()
    PageColumn(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
        }
        Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (webhookMissing) {
            Notice(stringResource(R.string.notice_webhook_missing), onOpenSettings)
        }
        if (health.availability == HcAvailability.AVAILABLE && health.missingCount > 0) {
            Notice(stringResource(R.string.notice_permissions_missing), onOpenSettings)
        }
        SectionCard(stringResource(R.string.section_sync)) {
            StatusRow(stringResource(R.string.status), statusLabel(sync.status))
            StatusRow(stringResource(R.string.last_success), ItalianDates.formatDateTime(sync.lastSuccess, zone))
            StatusRow(stringResource(R.string.last_attempt), ItalianDates.formatDateTime(sync.lastAttempt, zone))
            sync.httpCode?.let { StatusRow("HTTP", it.toString()) }
            sync.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            plan?.let { current ->
                Spacer(Modifier.height(6.dp))
                val cursor = ItalianDates.format(java.time.Instant.ofEpochMilli(current.cursorEpochMs).atZone(zone).toLocalDate())
                val end = ItalianDates.format(java.time.Instant.ofEpochMilli(current.endEpochMs).atZone(zone).toLocalDate())
                Text(
                    when {
                        current.finished -> stringResource(R.string.queue_done, current.sent)
                        current.phase == SendPlan.SCAN -> stringResource(R.string.queue_scanning)
                        else -> stringResource(R.string.queue_progress, current.sent, cursor, end)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (counts.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(
                        R.string.last_read,
                        counts["steps"] ?: 0,
                        counts["sleep"] ?: 0,
                        counts["weight"] ?: 0,
                        counts["exercise"] ?: 0,
                        counts["calories"] ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (calorieOnlySourceHint(counts)) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.calorie_only_hint), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = viewModel::syncNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_now)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = viewModel::reimport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reimport)) }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sync_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!ignoringBattery) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBattery, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.battery_button)) }
            }
        }
        Text(
            stringResource(R.string.footer, viewModel.appVersion),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onGrantRead: () -> Unit,
    onGrantBackground: () -> Unit,
    onGrantHistory: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val health = viewModel.health.value
    val validation = viewModel.validation.value
    var revealKey by remember { mutableStateOf(false) }
    val language = currentLanguage()
    PageColumn(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        SectionCard(stringResource(R.string.section_language)) {
            LanguageRow(stringResource(R.string.language_italian), language == "it") { setLanguage("it") }
            LanguageRow(stringResource(R.string.language_english), language == "en") { setLanguage("en") }
            Text(stringResource(R.string.language_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(R.string.section_webhook)) {
            OutlinedTextField(
                value = viewModel.url.value,
                onValueChange = { viewModel.url.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.webhook_url)) },
                placeholder = { Text(stringResource(R.string.webhook_url_placeholder)) },
                singleLine = true,
                isError = validation.urlError != null,
                supportingText = fieldError(validation.urlError)?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = viewModel.headerName.value,
                onValueChange = { viewModel.headerName.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.header_name)) },
                singleLine = true,
                isError = validation.headerNameError != null,
                supportingText = { Text(fieldError(validation.headerNameError) ?: stringResource(R.string.header_name_help)) },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = viewModel.headerValue.value,
                onValueChange = { viewModel.headerValue.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.header_value)) },
                placeholder = { Text(stringResource(R.string.header_value_placeholder)) },
                singleLine = true,
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                isError = validation.headerValueError != null,
                supportingText = { Text(fieldError(validation.headerValueError) ?: stringResource(R.string.header_value_help)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            TextButton(onClick = { revealKey = !revealKey }) {
                Text(stringResource(if (revealKey) R.string.hide_key else R.string.show_key))
            }
            OutlinedTextField(
                value = viewModel.startDate.value,
                onValueChange = { viewModel.startDate.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.start_date)) },
                placeholder = { Text(stringResource(R.string.start_date_placeholder)) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = viewModel.gapSeconds.value,
                onValueChange = { viewModel.gapSeconds.value = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.gap_seconds)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_settings)) }
        }
        SectionCard(stringResource(R.string.section_health)) {
            Text(stringResource(R.string.health_intro), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            when (health.availability) {
                HcAvailability.UNKNOWN -> Text(stringResource(R.string.health_checking))
                HcAvailability.UNAVAILABLE -> Text(stringResource(R.string.health_unavailable), color = MaterialTheme.colorScheme.error)
                HcAvailability.UPDATE_REQUIRED -> {
                    Text(stringResource(R.string.health_update), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    val context = LocalContext.current
                    Button(onClick = { openHealthConnectInstall(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.health_install))
                    }
                }
                HcAvailability.AVAILABLE -> {
                    val total = HealthPermissions.metrics.size
                    val granted = total - health.missingCount
                    Text(stringResource(R.string.permissions_count, granted, total))
                    if (health.missingCount == 0) {
                        Text(stringResource(R.string.permissions_all))
                    }
                    Spacer(Modifier.height(8.dp))
                    HealthPermissions.metrics.forEach { metric ->
                        val allowed = metric.permission in health.grantedPermissions
                        StatusRow(
                            stringResource(metric.labelRes),
                            stringResource(if (allowed) R.string.permission_granted else R.string.permission_denied),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(backgroundCopy(health.background), style = MaterialTheme.typography.bodyMedium)
                    Text(historyCopy(health.history), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onGrantRead, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.grant_read)) }
                    if (health.background == BackgroundAccess.DENIED && health.missingCount == 0) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onGrantBackground, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.grant_background))
                        }
                    } else if (health.background == BackgroundAccess.DENIED) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.grant_background_first), style = MaterialTheme.typography.bodySmall)
                    }
                    if (health.history == BackgroundAccess.DENIED && health.missingCount == 0) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onGrantHistory, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.grant_history))
                        }
                    } else if (health.history == BackgroundAccess.DENIED) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.grant_history_first), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenHealthConnect, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.open_health_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onOpen) { Text(stringResource(R.string.open_settings)) }
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun PageColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun statusLabel(status: SyncStatus): String = when (status) {
    SyncStatus.NEVER -> stringResource(R.string.status_never)
    SyncStatus.RUNNING -> stringResource(R.string.status_running)
    SyncStatus.SUCCESS -> stringResource(R.string.status_success)
    SyncStatus.FAILURE -> stringResource(R.string.status_failure)
}

@Composable
private fun backgroundCopy(access: BackgroundAccess): String = when (access) {
    BackgroundAccess.GRANTED -> stringResource(R.string.background_granted)
    BackgroundAccess.DENIED -> stringResource(R.string.background_denied)
    BackgroundAccess.UNSUPPORTED -> stringResource(R.string.background_unsupported)
    BackgroundAccess.UNKNOWN -> stringResource(R.string.background_unknown)
}

@Composable
private fun historyCopy(access: BackgroundAccess): String = when (access) {
    BackgroundAccess.GRANTED -> stringResource(R.string.history_granted)
    BackgroundAccess.DENIED -> stringResource(R.string.history_denied)
    BackgroundAccess.UNSUPPORTED -> stringResource(R.string.history_unsupported)
    BackgroundAccess.UNKNOWN -> stringResource(R.string.history_unknown)
}

@Composable
private fun fieldError(code: String?): String? {
    val res = when (code) {
        null -> return null
        FieldError.URL_REQUIRED -> R.string.err_url_required
        FieldError.URL_TOO_LONG -> R.string.err_url_too_long
        FieldError.URL_WHITESPACE -> R.string.err_url_whitespace
        FieldError.URL_INVALID -> R.string.err_url_invalid
        FieldError.URL_SCHEME -> R.string.err_url_scheme
        FieldError.URL_HOST -> R.string.err_url_host
        FieldError.HEADER_NAME_TOO_LONG -> R.string.err_header_name_too_long
        FieldError.HEADER_NAME_INVALID -> R.string.err_header_name_invalid
        FieldError.HEADER_VALUE_REQUIRED -> R.string.err_header_value_required
        FieldError.HEADER_VALUE_TOO_LONG -> R.string.err_header_value_too_long
        FieldError.HEADER_VALUE_NEWLINE -> R.string.err_header_value_newline
        else -> return code
    }
    return stringResource(res)
}

private fun currentLanguage(): String {
    val language = AppCompatDelegate.getApplicationLocales().get(0)?.language
    return if (language == "en") "en" else "it"
}

private fun setLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

private fun openHealthConnect(context: Context) {
    val steps = ExternalScreens.healthPermissionSteps(context.packageName, Build.VERSION.SDK_INT)
    if (ExternalScreens.launchFirst(steps) { context.startExternal(it) } == null) {
        context.toast(R.string.open_health_failed)
    }
}

private fun openHealthConnectInstall(context: Context) {
    val steps = ExternalScreens.healthInstallSteps(context.packageName)
    if (ExternalScreens.launchFirst(steps) { context.startExternal(it) } == null) {
        context.toast(R.string.open_health_failed)
    }
}

private fun requestBatteryExemption(context: Context) {
    val steps = ExternalScreens.batterySteps(context.packageName)
    if (ExternalScreens.launchFirst(steps) { context.startExternal(it) } == null) {
        context.toast(R.string.open_battery_failed)
    }
}
