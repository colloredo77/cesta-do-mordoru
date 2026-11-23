package cz.rodina.mordor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun SettingsScreen(vm: MainViewModel, family: Family, members: List<Member>, me: Member) {
    val ctx = LocalContext.current
    var name by remember(family.code) { mutableStateOf(family.name) }
    var city by remember(family.code) { mutableStateOf(family.city) }
    var base by remember(family.code) { mutableFloatStateOf(family.gollumBase.toFloat()) }
    var nBase by remember(family.code) { mutableFloatStateOf(family.nazgulBase.toFloat()) }
    val scope = rememberCoroutineScope()
    var bgOk by remember { mutableStateOf<Boolean?>(null) }
    var notifOk by remember { mutableStateOf(Notifier.canNotify(ctx)) }
    LaunchedEffect(Unit) { bgOk = runCatching { vm.health.available() && vm.health.hasBackgroundPermission() }.getOrDefault(false) }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        bgOk = vm.health.backgroundPermission in granted
        if (bgOk != true) vm.message = "Čtení na pozadí nepovoleno – ve 22:00 přijde jen připomínka."
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title("Můj profil")
        ScrollCard {
            ProfileEditor(me, members.filter { it.id != me.id }.map { it.character }.toSet(), vm.busy) { n, c, p, s ->
                vm.saveProfile(n, c, p, s)
            }
        }

        Title("Výprava")
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kód výpravy: ${family.code}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "Přidej se k výpravě do Mordoru! V aplikaci Cesta do Mordoru zadej kód: ${family.code}")
                    ctx.startActivity(Intent.createChooser(i, "Poslat kód"))
                }, modifier = Modifier.fillMaxWidth()) { Text("Poslat kód rodině") }
                OutlinedTextField(name, { name = it }, label = { Text("Název výpravy") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(city, { city = it }, label = { Text("Město pro počasí") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Glum ujde základně ${base.roundToLong().fmt()} kroků za noc (déšť a mlha víc, slunce méně).")
                Slider(value = base, onValueChange = { base = (it / 500f).toInt() * 500f }, valueRange = 3000f..15000f)
                Text("Nazgûl ujede základně ${nBase.toDouble().km()} za noc (tma a mraky víc, slunce a déšť méně).")
                Slider(value = nBase, onValueChange = { nBase = (it * 2).roundToInt() / 2f }, valueRange = 2f..12f)
                Button(onClick = { vm.updateFamily(name, city, base.roundToLong(), nBase.toDouble()) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Uložit nastavení výpravy")
                }
                Text("Výprava vyrazila: ${dayLabel(family.startDate)} (${family.startDate})", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
        }

        Title("Automatické nahrávání")
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Každý večer kolem 22:00 aplikace sama nahraje dnešní kroky. Když to nejde nebo je kroků málo, pošle připomínku.")
                Text("Čtení kroků na pozadí: " + when (bgOk) { true -> "povoleno ✓"; false -> "nepovoleno"; null -> "…" }, fontWeight = FontWeight.Bold)
                Text("Upozornění: " + if (notifOk) "povolena ✓" else "nepovolena", fontWeight = FontWeight.Bold)
                if (bgOk == false && vm.health.available()) {
                    OutlinedButton(onClick = { hcLauncher.launch(vm.health.allPermissions) }, modifier = Modifier.fillMaxWidth()) { Text("Povolit čtení na pozadí") }
                }
                if (!notifOk && Build.VERSION.SDK_INT >= 33) {
                    OutlinedButton(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text("Povolit upozornění") }
                }
                Text(
                    "Pokud Health Connect v telefonu čtení na pozadí neumí, přijde ve 22:00 aspoň připomínka. Android může úlohu o pár minut posunout.",
                    style = MaterialTheme.typography.bodySmall, color = InkSoft,
                )
            }
        }

        Title("Hodinky")
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Garmin: Garmin Connect → Více → Nastavení → Propojené aplikace → Health Connect → zapnout Kroky.")
                Text("Xiaomi: v Mi Fitness → Profil najděte propojení s Health Connect a povolte zápis kroků.")
                Text(
                    "Pokud se kroky počítají dvakrát (telefon + hodinky), v Health Connect → Data a přístup → Aktivita → Kroky dejte hodinky na první místo.",
                    style = MaterialTheme.typography.bodySmall, color = InkSoft,
                )
            }
        }

        Title("Aplikace")
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Verze ${BuildConfig.VERSION_NAME}")
                val upd = vm.update
                if (upd != null) {
                    Button(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(upd.url))) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Stáhnout novou verzi ${upd.version}")
                    }
                }
                OutlinedButton(onClick = { vm.checkUpdate(manual = true) }, modifier = Modifier.fillMaxWidth()) { Text("Zkontrolovat aktualizace") }
                Text(
                    "Stažený soubor otevřete a dejte Instalovat – nová verze se nainstaluje přes starou a nic se neztratí.",
                    style = MaterialTheme.typography.bodySmall, color = InkSoft,
                )
            }
        }

        Title("Správa výpravy")
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmRestart = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Začít cestu znovu (od Hobitína)")
                }
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Smazat celou výpravu", color = Blood)
                }
                TextButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Jen odejít z výpravy v tomto telefonu", color = InkSoft)
                }
            }
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            containerColor = Parchment,
            title = { Text("Začít cestu znovu?") },
            text = { Text("Všichni se vrátí do Hobitína a Glum znovu vyrazí za vámi. Členové, fotky a postavy zůstanou. Platí pro celou rodinu.") },
            confirmButton = { TextButton(onClick = { confirmRestart = false; vm.restartJourney() }) { Text("Začít znovu", color = Blood) } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Zpět") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Parchment,
            title = { Text("Smazat výpravu?") },
            text = { Text("Smaže se výprava ${family.code} i všichni členové a jejich kroky – na všech telefonech. Nejde to vrátit.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteFamily() }) { Text("Smazat", color = Blood) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zpět") } },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = Parchment,
            title = { Text("Odejít z výpravy?") },
            text = { Text("Data výpravy zůstanou uložená. Vrátit se můžete zadáním kódu ${family.code} a výběrem svého profilu.") },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("Odejít", color = Blood) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Zůstat") } },
        )
    }
}
