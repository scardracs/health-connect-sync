package it.healthsync.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.healthsync.app.data.SyncStatus
import it.healthsync.app.domain.HistoryRange
import it.healthsync.app.health.HealthPermissions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val backfill by viewModel.backfill.collectAsStateWithLifecycle()
    val health = viewModel.health.value
    val validation = viewModel.validation.value
    val syncing = viewModel.syncing.value
    var revealKey by remember { mutableStateOf(false) }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Salute Sync", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "I dati che autorizzi in Health Connect partono verso il webhook che scegli tu, una volta al giorno.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionCard(title = "Health Connect") {
                    Text(
                        "Samsung Health non viene letto direttamente. I dati arrivano solo se Samsung Health li sincronizza con Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    when (health.availability) {
                        HcAvailability.UNKNOWN -> {
                            Text("Verifica di Health Connect in corso…")
                        }
                        HcAvailability.UNAVAILABLE -> {
                            Text(
                                "Health Connect non è disponibile su questo dispositivo.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        HcAvailability.UPDATE_REQUIRED -> {
                            Text(
                                "Health Connect non è installato o va aggiornato.",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { openHealthConnectInstall(context) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Installa o aggiorna Health Connect") }
                        }
                        HcAvailability.AVAILABLE -> {
                            val granted = health.grantedLabels.size
                            val total = HealthPermissions.metrics.size
                            Text("Permessi di lettura: $granted di $total.")
                            if (health.missingLabels.isEmpty()) {
                                Text("Tutti i permessi di lettura sono concessi.")
                            } else {
                                Text(
                                    "Mancano: ${health.missingLabels.joinToString(", ")}.",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(backgroundCopy(health.background), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        readPermissionLauncher.launch(HealthPermissions.readPermissions)
                                    } catch (_: Exception) {
                                        viewModel.banner.value = "Impossibile aprire la richiesta permessi di Health Connect."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Concedi permessi di lettura") }
                            if (health.background == BackgroundAccess.DENIED && health.missingLabels.isEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            backgroundPermissionLauncher.launch(setOf(HealthPermissions.background))
                                        } catch (_: Exception) {
                                            viewModel.banner.value =
                                                "Impossibile chiedere la lettura in background. Aggiorna Health Connect."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Consenti lettura in background") }
                            } else if (health.background == BackgroundAccess.DENIED) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Concedi prima tutti i permessi di lettura, poi la lettura in background.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(historyCopy(health.history), style = MaterialTheme.typography.bodyMedium)
                            if (health.history == BackgroundAccess.DENIED && health.missingLabels.isEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            historyPermissionLauncher.launch(setOf(HealthPermissions.history))
                                        } catch (_: Exception) {
                                            viewModel.banner.value =
                                                "Impossibile chiedere la lettura dello storico. Aggiorna Health Connect."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Consenti lettura dello storico") }
                            } else if (health.history == BackgroundAccess.DENIED) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Concedi prima tutti i permessi di lettura, poi la lettura dello storico.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { openHealthConnect(context) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Apri le impostazioni di Health Connect") }
                        }
                    }
                }

                SectionCard(title = "Webhook") {
                    OutlinedTextField(
                        value = viewModel.url.value,
                        onValueChange = { viewModel.url.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("URL del webhook") },
                        placeholder = { Text("https://esempio.it/salute") },
                        singleLine = true,
                        isError = validation.urlError != null,
                        supportingText = validation.urlError?.let { error -> { Text(error) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.headerName.value,
                        onValueChange = { viewModel.headerName.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome header") },
                        singleLine = true,
                        isError = validation.headerNameError != null,
                        supportingText = {
                            Text(validation.headerNameError ?: "Predefinito: Authorization.")
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.headerValue.value,
                        onValueChange = { viewModel.headerValue.value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Valore header") },
                        placeholder = { Text("Bearer la-tua-chiave") },
                        singleLine = true,
                        visualTransformation = if (revealKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        isError = validation.headerValueError != null,
                        supportingText = {
                            Text(
                                validation.headerValueError
                                    ?: "Inviato così com'è. Salvato cifrato sul telefono.",
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    TextButton(onClick = { revealKey = !revealKey }) {
                        Text(if (revealKey) "Nascondi chiave" else "Mostra chiave")
                    }
                    Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                        Text("Salva impostazioni")
                    }
                }

                SectionCard(title = "Sincronizzazione") {
                    StatusRow("Stato", statusLabel(sync.status))
                    StatusRow("Ultima riuscita", formatInstant(sync.lastSuccess, zone))
                    StatusRow("Ultimo tentativo", formatInstant(sync.lastAttempt, zone))
                    sync.httpCode?.let { code ->
                        StatusRow("HTTP", code.toString())
                    }
                    sync.message?.let { message ->
                        Spacer(Modifier.height(6.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                    backfill?.let { plan ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (plan.finished) {
                                "Storico completo importato (${plan.completed} blocchi)."
                            } else {
                                "Importazione storica: ${plan.completed} di ${plan.total} blocchi inviati."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = viewModel::syncNow,
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("  Sincronizzazione…")
                        } else {
                            Text("Sincronizza ora")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::importHistory,
                        enabled = !syncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Importa storico completo") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "La prima sincronizzazione importa tutto lo storico disponibile, a blocchi mensili. Poi, ogni giorno intorno alle 6:00 ($zone), partono solo i dati nuovi. Senza il permesso di lettura dello storico il limite è ${HistoryRange.FALLBACK_DAYS} giorni prima della prima concessione. Android può ritardare il lavoro se il risparmio energetico è attivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!ignoringBattery) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { requestBatteryExemption(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Non ottimizzare la batteria") }
                    }
                }

                Text(
                    "Ogni invio contiene i totali giornalieri (passi, distanza, calorie, cuore, idratazione), le sessioni di sonno con le fasi, peso, massa grassa e allenamenti. Versione ${viewModel.appVersion}. Nessun tracciamento e nessun server dell'app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun statusLabel(status: SyncStatus): String = when (status) {
    SyncStatus.NEVER -> "Mai sincronizzato"
    SyncStatus.RUNNING -> "In corso"
    SyncStatus.SUCCESS -> "Riuscita"
    SyncStatus.FAILURE -> "Non riuscita"
}

private fun historyCopy(access: BackgroundAccess): String = when (access) {
    BackgroundAccess.GRANTED ->
        "Lettura dello storico concessa. L'importazione completa parte da ${HistoryRange.HORIZON_YEARS} anni fa, o da quando i dati esistono."
    BackgroundAccess.DENIED ->
        "Senza il permesso di lettura dello storico, Health Connect consente solo i dati da 30 giorni prima della prima concessione dei permessi. L'importazione userà quella finestra."
    BackgroundAccess.UNSUPPORTED ->
        "Questo dispositivo non supporta la lettura dello storico. L'importazione copre al massimo i 30 giorni precedenti la prima concessione dei permessi."
    BackgroundAccess.UNKNOWN -> "Permesso di lettura dello storico non verificato."
}

private fun backgroundCopy(access: BackgroundAccess): String = when (access) {
    BackgroundAccess.GRANTED -> "Lettura in background concessa: la sync delle 6:00 può leggere i dati a schermo spento."
    BackgroundAccess.DENIED -> "Lettura in background non concessa. Senza questo permesso la sync automatica non legge i dati."
    BackgroundAccess.UNSUPPORTED -> "Lettura in background non supportata su questo dispositivo. Usa Sincronizza ora con l'app aperta."
    BackgroundAccess.UNKNOWN -> "Permesso di lettura in background non verificato."
}

private val uiStamp: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ITALY)

private fun formatInstant(instant: Instant?, zone: ZoneId): String {
    if (instant == null) return "—"
    return uiStamp.format(instant.atZone(zone))
}

private fun openHealthConnect(context: Context) {
    val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openHealthConnectInstall(context)
    }
}

private fun openHealthConnectInstall(context: Context) {
    val market = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.google.android.apps.healthdata"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun requestBatteryExemption(context: Context) {
    val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    try {
        context.startActivity(request)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            // The screen still explains that Android may defer the morning sync.
        }
    }
}
