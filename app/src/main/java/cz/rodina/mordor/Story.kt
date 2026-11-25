package cz.rodina.mordor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Karta zastávky s příběhem. */
@Composable
fun StoryDialog(wp: Waypoint, game: GameState?, chapter: Boolean = false, onDismiss: () -> Unit) {
    val reached = game?.members?.filter { it.km >= wp.km }?.map { it.member.name }.orEmpty()
    val idx = Route.majors.indexOfFirst { it.name == wp.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Parchment,
        title = {
            Column {
                if (chapter) Text(if (wp.minor) "Nová zastávka" else "Nová kapitola", style = MaterialTheme.typography.labelLarge, color = Blood)
                Text(
                    (if (!wp.minor && idx >= 0) "${idx + 1}. " else "") + wp.name,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${wp.km.km()} od Hobitína" + (if (wp.minor) " · mezizastávka" else ""),
                    style = MaterialTheme.typography.bodySmall, color = InkSoft,
                )
                Text(wp.story, style = MaterialTheme.typography.bodyLarge)
                if (!wp.minor && wp.note != wp.story) {
                    Text(wp.note, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = InkSoft)
                }
                if (reached.isNotEmpty()) {
                    Text("Už tu byli: ${reached.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = Elf)
                }
            }
        },
        confirmButton = {
            if (chapter) Button(onClick = onDismiss) { Text("Pokračovat v cestě") }
            else TextButton(onClick = onDismiss) { Text("Zavřít") }
        },
    )
}
