package cz.rodina.mordor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Ranní hlášení: co se stalo včera a v noci. Zobrazí se jednou denně. */
@Composable
fun MorningReport(game: GameState, colors: Map<String, Color>, forecast: Forecast?, family: Family, onDismiss: () -> Unit) {
    val yesterday = LocalDate.now().minusDays(1).toString()
    val log = game.days.firstOrNull { it.day == yesterday } ?: return
    val events = game.events.filter { it.day == yesterday && it.kind != Kind.START }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Parchment,
        title = { Text("❦ Co se stalo v noci ❦", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Včera ušli:", style = MaterialTheme.typography.titleSmall, color = Blood)
                game.members.sortedByDescending { it.member.stepsOn(yesterday) }.forEach { ms ->
                    val s = ms.member.stepsOn(yesterday)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(ms.member.photo, ms.member.name, 30.dp, colors[ms.member.id] ?: Gold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${ms.member.name}: ${s.fmt()} kroků (${(s * ms.member.strideCm / 100_000.0).km()})" +
                                if (s >= family.lembasThreshold) " · +1 🍞" else "",
                        )
                    }
                }
                Text("Celkem výprava: ${log.familySteps.fmt()} kroků", style = MaterialTheme.typography.bodySmall, color = InkSoft)

                HorizontalDivider(color = Gold.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                Text("Nepřátelé v noci:", style = MaterialTheme.typography.titleSmall, color = Blood)
                log.gollum?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GollumMarker(26.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Glum +${Gollum.km(it.steps).km()} – ${it.reason.lowercase()}")
                    }
                }
                log.nazgul?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NazgulMarker(26.dp, winged = game.nazgulPhase == 2)
                        Spacer(Modifier.width(8.dp))
                        Text("Nazgûl +${it.km.km()} – ${it.reason.lowercase()}")
                    }
                }

                val newBadges = game.members.flatMap { ms ->
                    ms.badges.filter { it.value == yesterday }.keys.mapNotNull { id -> Badges.byId(id)?.let { ms.member.name to it } }
                }
                if (newBadges.isNotEmpty()) {
                    HorizontalDivider(color = Gold.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    Text("Nové odznaky:", style = MaterialTheme.typography.titleSmall, color = Blood)
                    newBadges.forEach { (name, b) -> Text("${b.icon} $name – ${b.name}") }
                }

                if (events.isNotEmpty()) {
                    HorizontalDivider(color = Gold.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    Text("Události:", style = MaterialTheme.typography.titleSmall, color = Blood)
                    events.forEach {
                        Text("• ${it.text}", color = if (it.kind == Kind.CAUGHT) Blood else Ink)
                    }
                }

                HorizontalDivider(color = Gold.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                Text("Jak to vypadá teď:", style = MaterialTheme.typography.titleSmall, color = Blood)
                game.last?.let { Text("Nejblíž Glumovi: ${it.member.name} – ${(it.km - game.gollumKm).km()}") }
                val rb = game.ringBearer
                if (game.nazgulActive && rb != null) Text("Nazgûl za Prstenonošem (${rb.member.name}): ${(rb.km - game.nazgulKm).km()}")
                forecast?.let {
                    Text(
                        "Dnes v noci: Glum asi +${Gollum.km(it.gollum.steps).km()}" +
                            (if (game.nazgulActive) ", Nazgûl asi +${(it.nazgul.km * (if (game.nazgulPhase == 2) Nazgul.WINGED_SPEED else 1.0)).km()}" else "") + ".",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Vyrazit!") } },
    )
}
