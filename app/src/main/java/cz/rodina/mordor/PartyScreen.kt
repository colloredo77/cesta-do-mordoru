package cz.rodina.mordor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PartyScreen(vm: MainViewModel, family: Family, game: GameState, me: Member, colors: Map<String, Color>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editFor by remember { mutableStateOf<Member?>(null) }

    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(vm.health.permissions)) vm.syncHealth()
        else vm.message = "Bez oprávnění kroky nenačtu – můžete je zadávat ručně."
    }

    fun sync() {
        when (vm.health.status()) {
            HealthConnectClient.SDK_AVAILABLE -> scope.launch {
                val ok = runCatching { vm.health.hasPermission() }.getOrDefault(false)
                if (ok) vm.syncHealth() else permLauncher.launch(vm.health.permissions)
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                vm.message = "Nainstalujte nebo aktualizujte Health Connect."
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
                }
            }
            else -> vm.message = "Health Connect v tomto telefonu není – zadávejte kroky ručně."
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Title(family.name) }
        item {
            ScrollCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { sync() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Načíst moje kroky z hodinek") }
                    Text(
                        "Garmin i Xiaomi posílají kroky přes Health Connect. Poslední načtení: ${vm.lastSync ?: "zatím nikdy"}.",
                        style = MaterialTheme.typography.bodySmall, color = InkSoft,
                    )
                }
            }
        }
        items(game.members, key = { it.member.id }) { ms ->
            MemberCard(ms, colors[ms.member.id] ?: Gold, isMe = ms.member.id == me.id) { editFor = ms.member }
        }
        item {
            Text(
                "Ručně zadané kroky mají přednost před hodinkami. Zadat je jde i za ostatní (třeba za člena bez hodinek).",
                style = MaterialTheme.typography.bodySmall, color = InkSoft,
            )
        }
    }

    editFor?.let { m ->
        StepsDialog(m, family, onDismiss = { editFor = null }) { day, steps -> vm.setManual(m.id, day, steps) }
    }
}

@Composable
private fun MemberCard(ms: MemberState, color: Color, isMe: Boolean, onEdit: () -> Unit) {
    val m = ms.member
    ScrollCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(m.photo, m.name, 56.dp, color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name + if (isMe) " (já)" else "", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(m.character, color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    Text("Dnes: ${ms.todaySteps.fmt()} kroků", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = onEdit) { Text("Kroky") }
            }
            LinearProgressIndicator(
                progress = { (ms.km / Route.totalKm).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = ParchmentDark,
            )
            Text(
                "${ms.km.km()} · ${Route.lastReached(ms.km).name} · celkem ${ms.totalSteps.fmt()} kroků" +
                    if (ms.caught > 0) " · Glum dostihl ${ms.caught}×" else "",
                style = MaterialTheme.typography.bodySmall, color = InkSoft,
            )
        }
    }
}

@Composable
private fun StepsDialog(m: Member, family: Family, onDismiss: () -> Unit, onSave: (String, Long?) -> Unit) {
    val today = LocalDate.now()
    val start = runCatching { LocalDate.parse(family.startDate) }.getOrDefault(today)
    val days = (0L..6L).map { today.minusDays(it) }.filter { !it.isBefore(start) }.ifEmpty { listOf(today) }
    var day by remember { mutableStateOf(days.first()) }
    val key = day.toString()
    var text by remember(key) { mutableStateOf(m.manual[key]?.toString() ?: "") }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Parchment,
        title = { Text("Kroky – ${m.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { menu = true }) { Text("Den: ${dayLabel(day)}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        days.forEach { d -> DropdownMenuItem(text = { Text(dayLabel(d)) }, onClick = { day = d; menu = false }) }
                    }
                }
                OutlinedTextField(
                    text, { text = it.filter(Char::isDigit).take(6) },
                    label = { Text("Počet kroků (ručně)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("Z hodinek: ${(m.health[key] ?: 0L).fmt()} kroků", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Text("Prázdné pole = použít hodnotu z hodinek.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key, text.toLongOrNull()); onDismiss() }) { Text("Uložit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zavřít") } },
    )
}
