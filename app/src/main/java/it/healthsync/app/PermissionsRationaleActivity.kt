package it.healthsync.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.healthsync.app.ui.theme.SaluteSyncTheme

class PermissionsRationaleActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapItalian(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaluteSyncTheme {
                PrivacyText()
            }
        }
    }
}

@Composable
private fun PrivacyText() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Text(
                "Perché Salute Sync legge questi dati",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                """
                Salute Sync legge solo i dati che autorizzi in Health Connect: passi, distanza, piani, dislivello, velocità, potenza, calorie, allenamenti, sonno e fasi, peso e misure corporee, frequenza cardiaca, variabilità, ossigeno, pressione, glicemia, temperatura, respirazione, VO2 max, nutrizione e idratazione.

                Una volta al giorno, o quando tocchi «Sincronizza ora», l'app invia un JSON all'indirizzo webhook che hai incollato tu. L'URL e la chiave restano sul telefono, cifrati. Non c'è un server di Salute Sync, né analisi, né tracker di terze parti.

                Puoi revocare i permessi in qualsiasi momento da Health Connect. Senza la lettura in background la sincronizzazione automatica a schermo spento non può leggere i dati. Senza la lettura dello storico l'importazione copre solo circa 30 giorni prima della prima concessione. In entrambi i casi puoi usare «Sincronizza ora» o «Importa storico completo» con l'app aperta.
                """.trimIndent(),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
