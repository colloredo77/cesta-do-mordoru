package cz.rodina.mordor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChronicleScreen(game: GameState) {
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Title("Kronika výpravy") }
        items(game.events.reversed()) { e ->
            ScrollCard {
                Column {
                    Text(dayLabel(e.day), style = MaterialTheme.typography.labelMedium, color = InkSoft)
                    Text(
                        e.text,
                        color = when (e.kind) { Kind.CAUGHT -> Blood; Kind.FINISH -> Gold; Kind.ENEMY -> River; else -> Ink },
                        fontWeight = if (e.kind == Kind.WAYPOINT) FontWeight.Normal else FontWeight.Bold,
                    )
                }
            }
        }
        item { Title("Den po dni") }
        items(game.days.reversed()) { d ->
            Column {
                Text(dayLabel(d.day), fontWeight = FontWeight.Bold)
                Text("Výprava: ${d.familySteps.fmt()} kroků", style = MaterialTheme.typography.bodySmall)
                Text(
                    d.gollum?.let { "Glum: +${Gollum.km(it.steps).km()} – ${it.reason}" } ?: "Glum: vyrazí v noci…",
                    style = MaterialTheme.typography.bodySmall, color = InkSoft,
                )
                d.nazgul?.let {
                    Text("Nazgûl: +${it.km.km()} – ${it.reason}", style = MaterialTheme.typography.bodySmall, color = River)
                }
            }
        }
    }
}
