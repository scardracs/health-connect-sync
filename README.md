# Salute Sync

App Android che legge i dati dell'utente da **Health Connect** (dove Samsung Health può sincronizzarli) e li invia in JSON a un webhook, una volta al giorno.

Non c'è un backend, non ci sono analytics e non ci sono tracker di terze parti. L'URL e la chiave restano sul telefono, cifrati con `EncryptedSharedPreferences` (AES-256, chiave nell'Android Keystore).

- Kotlin, Jetpack Compose
- `minSdk` 28, `compileSdk` / `targetSdk` 37 (Android 17)
- Health Connect `connect-client` 1.1.0
- WorkManager, sync intorno alle 6:00 nel fuso del telefono

## Installazione sul telefono

L'APK da sideloadare è firmato (vedi sotto) e si chiama `salute-sync-1.0.0.apk`.

1. Copia l'APK sul telefono (Drive, mail, cavo, o scaricalo dal browser).
2. Aprilo. Se Android blocca l'installazione, consenti **Installa app sconosciute** per l'app da cui lo stai aprendo (Chrome, File, Gmail, …). Su Android 8 e successivi il permesso è per singola app, non un interruttore globale: Impostazioni → App → l'app usata per aprire il file → Installa app sconosciute.
3. Conferma l'installazione e apri **Salute Sync**.
4. Se il telefono è Android 13 o precedente e Health Connect manca, tocca **Installa o aggiorna Health Connect** (Play Store, pacchetto `com.google.android.apps.healthdata`). Su Android 14 e successivi Health Connect è nel sistema: Impostazioni → Sicurezza e privacy → Controlli privacy → Health Connect, oppure cerca «Health Connect».
5. In **Samsung Health** attiva la sincronizzazione verso Health Connect per i tipi che ti servono (passi, sonno, peso, allenamenti, frequenza cardiaca, e gli altri). Il percorso del menu cambia con la versione dell'app: cerca «Health Connect» nelle impostazioni di Samsung Health e controlla poi, in Health Connect, che Samsung Health compaia tra le sorgenti.
6. In Salute Sync tocca **Concedi permessi di lettura** e accetta i tipi che vuoi inviare. Quelli negati non vengono letti e finiscono in `unavailableMetrics`, senza essere trattati come zeri.
7. Se il telefono lo supporta, tocca **Consenti lettura in background**. Senza quel permesso la sync delle 6:00 non può leggere a schermo spento: resta disponibile **Sincronizza ora**, con l'app aperta.
8. Incolla l'URL del webhook (`https://` consigliato; `http://` è ammesso, per esempio su una rete locale) e il valore dell'header. Il nome dell'header è `Authorization` e si può cambiare.
9. Tocca **Salva impostazioni**, poi **Sincronizza ora**.
10. Controlla in basso l'ora e l'esito dell'ultima sync. La sync automatica è un lavoro WorkManager ogni 24 ore, con il primo avvio puntato alle 6:00 del fuso del telefono (in Italia, `Europe/Rome`). Android può ritardarlo se il risparmio energetico è attivo: il pulsante **Non ottimizzare la batteria** apre la richiesta di sistema.

Per aggiornare l'app, installa sopra lo stesso APK firmato con la stessa chiave. Disinstallare cancella URL, chiave e l'istante dell'ultima sync.

## Cosa viene inviato

`POST` sull'URL salvato.

- `Content-Type: application/json; charset=utf-8`
- Header con il nome e il valore che hai impostato (default `Authorization`)
- `User-Agent: SaluteSync/1.0.0`

La prima sync copre gli **ultimi 7 giorni**. Quelle dopo partono dall'istante dell'ultima sync **riuscita** e arrivano a «adesso». L'intervallo è `[inizio, fine)`: il record che cade esattamente sulla fine entra nella sync successiva, così non si duplica e non si perde.

I totali giornalieri (passi, distanza, calorie, frequenza cardiaca, frequenza a riposo, idratazione) arrivano dall'aggregazione di Health Connect, solo sulla porzione di giorno inclusa nella finestra. Così passi scritti da più sorgenti non vengono sommati due volte. Variabilità cardiaca, ossigeno e nutrizione sono calcolati sui campioni letti. Sonno, peso, massa grassa e allenamenti restano eventi singoli.

Una sessione di sonno o un allenamento entra nel JSON se l'**inizio** cade nella finestra; durata e fasi sono quelle intere, anche se la fine sfora. Le calorie e la distanza di un allenamento sono l'aggregato di Health Connect sull'intervallo della sessione.

Se Health Connect rifiuta una finestra più lunga di 30 giorni prima della concessione del permesso, l'app riprova sugli ultimi 30 giorni e mette `"historyLimited": true`. Il permesso di storico oltre quel limite non è richiesto.

In caso di errore di rete, HTTP 408, 429 o 5xx, WorkManager riprova con backoff esponenziale (minimo 15 minuti, fino a 5 tentativi in quel ciclo). Un URL o una chiave sbagliati (4xx) non vengono martellati. La sync periodica resta in agenda comunque.

## Schema JSON (versione 1)

I campi null non vengono scritti. I numeri decimali sono arrotondati a 2 cifre. Gli istanti sono ISO-8601 con l'offset del fuso del telefono. `unavailableMetrics` elenca gli id non concessi, in ordine alfabetico.

```json
{
  "schemaVersion": 1,
  "syncedAt": "2026-01-17T06:00:00+01:00",
  "timezone": "Europe/Rome",
  "appVersion": "1.0.0",
  "historyLimited": false,
  "window": {
    "start": "2026-01-16T06:00:00+01:00",
    "end": "2026-01-17T06:00:00+01:00"
  },
  "days": [
    {
      "date": "2026-01-16",
      "steps": 8432,
      "distanceMeters": 6120.5,
      "activeCaloriesKcal": 320.4,
      "totalCaloriesKcal": 2100.0,
      "heartRate": { "min": 52, "avg": 72, "max": 145, "sampleCount": 120 },
      "restingHeartRateBpm": 58,
      "heartRateVariabilityRmssdMillis": { "min": 20.0, "avg": 42.5, "max": 65.0, "sampleCount": 2 },
      "oxygenSaturationPercent": { "min": 97.0, "avg": 98.0, "max": 99.0, "sampleCount": 2 },
      "nutrition": {
        "energyKcal": 500.0,
        "proteinGrams": 30.0,
        "carbohydrateGrams": 40.0,
        "fatGrams": 10.0,
        "mealCount": 2
      },
      "hydrationMilliliters": 1800.0
    }
  ],
  "sleepSessions": [
    {
      "start": "2026-01-16T23:00:00+01:00",
      "end": "2026-01-17T07:00:00+01:00",
      "durationMinutes": 480,
      "notes": "casa",
      "stages": [
        {
          "stage": "deep",
          "start": "2026-01-16T23:30:00+01:00",
          "end": "2026-01-17T01:00:00+01:00",
          "durationMinutes": 90
        },
        {
          "stage": "rem",
          "start": "2026-01-17T05:00:00+01:00",
          "end": "2026-01-17T06:00:00+01:00",
          "durationMinutes": 60
        }
      ]
    }
  ],
  "weightReadings": [
    { "time": "2026-01-16T08:00:00+01:00", "weightKg": 74.2 }
  ],
  "bodyFatReadings": [
    { "time": "2026-01-16T08:00:00+01:00", "percentage": 18.5 }
  ],
  "exerciseSessions": [
    {
      "start": "2026-01-16T18:00:00+01:00",
      "end": "2026-01-16T18:45:00+01:00",
      "durationMinutes": 45,
      "exerciseType": "running",
      "title": "Parco",
      "activeCaloriesKcal": 420.0,
      "distanceMeters": 7000.0
    }
  ],
  "unavailableMetrics": []
}
```

### Campi

| Campo | Significato |
| --- | --- |
| `schemaVersion` | Sempre `1` in questa app. |
| `syncedAt` | Istante in cui l'app ha costruito il JSON. |
| `timezone` | `ZoneId` del telefono, per esempio `Europe/Rome`. |
| `appVersion` | `versionName` dell'app. |
| `historyLimited` | `true` se la finestra è stata accorciata al limite dei 30 giorni di Health Connect. |
| `window.start` / `window.end` | Intervallo letto, fine esclusa. |
| `days[]` | Un oggetto per giorno locale che ha almeno un dato. I giorni vuoti non compaiono. `steps: 0` resta, perché è un totale vero. |
| `heartRate` | Minimo, media e massimo in bpm, più il numero di campioni, dall'aggregato Health Connect. |
| `restingHeartRateBpm` | Media dei rilevamenti a riposo di quel giorno (`BPM_AVG`). |
| `heartRateVariabilityRmssdMillis` | Min / media / max della RMSSD, in millisecondi. |
| `oxygenSaturationPercent` | Min / media / max della saturazione, in percentuale (non in frazione). |
| `nutrition` | Somma di energia, proteine, carboidrati e grassi dei pasti iniziati quel giorno. `mealCount` conta anche i pasti senza quei campi. |
| `hydrationMilliliters` | Volume totale nel pezzo di giorno incluso nella finestra. |
| `sleepSessions[].stages[].stage` | `awake`, `sleeping`, `out_of_bed`, `light`, `deep`, `rem`, `awake_in_bed`, `unknown`. |
| `exerciseSessions[].exerciseType` | Nome in snake_case del tipo Health Connect. Vedi elenco sotto. Un codice futuro non mappato diventa `exercise_<numero>`. |
| `unavailableMetrics` | Id non letti perché il permesso manca: `steps`, `distance`, `active_calories`, `total_calories`, `exercise_session`, `sleep_session`, `weight`, `body_fat`, `heart_rate`, `resting_heart_rate`, `heart_rate_variability`, `oxygen_saturation`, `nutrition`, `hydration`. |

Tipi di allenamento: `other_workout`, `badminton`, `baseball`, `basketball`, `biking`, `biking_stationary`, `boot_camp`, `boxing`, `calisthenics`, `cricket`, `dancing`, `elliptical`, `exercise_class`, `fencing`, `football_american`, `football_australian`, `frisbee_disc`, `golf`, `guided_breathing`, `gymnastics`, `handball`, `high_intensity_interval_training`, `hiking`, `ice_hockey`, `ice_skating`, `martial_arts`, `paddling`, `paragliding`, `pilates`, `racquetball`, `rock_climbing`, `roller_hockey`, `rowing`, `rowing_machine`, `rugby`, `running`, `running_treadmill`, `sailing`, `scuba_diving`, `skating`, `skiing`, `snowboarding`, `snowshoeing`, `soccer`, `softball`, `squash`, `stair_climbing`, `stair_climbing_machine`, `strength_training`, `stretching`, `surfing`, `swimming_open_water`, `swimming_pool`, `table_tennis`, `tennis`, `volleyball`, `walking`, `water_polo`, `weightlifting`, `wheelchair`, `yoga`.

## Firma dell'APK

La build `release` è firmata con il keystore nel repository `keystore/salute-sync.jks` (alias `salutesync`, password store e chiave `salutesync`). È una chiave di sideload, pubblica di proposito, non una chiave da Play Store. Va bene per installare e aggiornare questa app fuori dallo store. Non va usata per pubblicare su Google Play.

`EncryptedSharedPreferences` è l'API richiesta per URL e chiave. Jetpack l'ha segnata come deprecata in `security-crypto` 1.1.0 e non ha un sostituto ufficiale: qui resta in uso, con una ricreazione del file se il keyset risulta illeggibile.

## Compilare in locale

Serve JDK 17 o successivo e Android SDK 37 (platform `android-37` / `android-37.0` e build-tools 37).

```bash
export ANDROID_HOME="$HOME/android-sdk"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

L'APK è in `app/build/outputs/apk/release/salute-sync-release.apk`.

I test di unità coprono la costruzione del JSON (totali, sonno con fasi, peso, allenamenti, fuso `Europe/Rome`), la finestra dei 7 giorni e delle sync successive, l'orario delle 6:00, e la validazione di URL e header.
