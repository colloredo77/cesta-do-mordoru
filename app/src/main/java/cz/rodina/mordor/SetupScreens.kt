package cz.rodina.mordor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FamilySetup(vm: MainViewModel) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Title("Cesta do Mordoru")
        Text("verze ${BuildConfig.VERSION_CODE}", color = InkSoft, style = MaterialTheme.typography.bodySmall)
        Text(
            "Každý krok z hodinek vás posune Středozemí od Hobitína až k Hoře osudu. A Glum vám jde v patách.",
            textAlign = TextAlign.Center, color = InkSoft,
        )
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Založit novou výpravu", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(name, { name = it }, label = { Text("Název (např. Společenstvo Novákových)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(city, { city = it }, label = { Text("Kde bydlíte (počasí pro Gluma)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.createFamily(name, city) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Založit výpravu") }
            }
        }
        Text("— nebo —", color = InkSoft)
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Připojit se k výpravě", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    code, { code = it.uppercase().take(6) }, label = { Text("Kód výpravy (6 znaků)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.joinFamily(code) }, enabled = !vm.busy && code.length == 6, modifier = Modifier.fillMaxWidth()) { Text("Připojit se") }
            }
        }
    }
}

@Composable
fun ProfileSetup(vm: MainViewModel, members: List<Member>) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Title("Kdo jsi, poutníku?")
        if (members.isNotEmpty()) {
            ScrollCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Už jsi ve výpravě? Vyber se:", style = MaterialTheme.typography.titleSmall)
                    members.forEachIndexed { i, m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.claimMember(m.id) }.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(m.photo, m.name, 40.dp, MemberColors[i % MemberColors.size])
                            Spacer(Modifier.width(10.dp))
                            Text("${m.name} – ${m.character}")
                        }
                    }
                }
            }
        }
        ScrollCard {
            ProfileEditor(null, members.map { it.character }.toSet(), vm.busy) { n, c, p, s -> vm.saveProfile(n, c, p, s) }
        }
        TextButton(onClick = { vm.leave() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Zpět – jiná výprava") }
    }
}

@Composable
fun ProfileEditor(initial: Member?, taken: Set<String>, busy: Boolean, onSave: (String, String, String?, Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var character by remember(initial?.id) {
        mutableStateOf(initial?.character ?: (Characters.list.firstOrNull { it !in taken } ?: Characters.list[0]))
    }
    var photo by remember(initial?.id) { mutableStateOf(initial?.photo) }
    var stride by remember(initial?.id) { mutableFloatStateOf((initial?.strideCm ?: 70).toFloat()) }
    var menu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { encodePhoto(ctx, uri)?.let { photo = it } }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(photo, name.ifBlank { character }, 104.dp, Gold, Modifier.clickable { pick() })
        TextButton(onClick = { pick() }) { Text(if (photo == null) "Vybrat fotku" else "Změnit fotku") }
        OutlinedTextField(
            name, { name = it.take(20) }, label = { Text("Jméno") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Postava: $character") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Characters.list.forEach { c ->
                    val busyChar = c in taken && c != initial?.character
                    DropdownMenuItem(
                        text = { Text(if (busyChar) "$c (už má někdo)" else c, color = if (busyChar) InkSoft else Ink) },
                        onClick = { character = c; menu = false },
                    )
                }
            }
        }
        Text("Délka kroku: ${stride.roundToInt()} cm", modifier = Modifier.fillMaxWidth())
        Slider(value = stride, onValueChange = { stride = it }, valueRange = 45f..95f)
        Text("Dospělý zhruba 70–80 cm, dítě 55–65 cm. Z kroků se počítají kilometry.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        Button(
            enabled = !busy && name.isNotBlank(),
            onClick = { onSave(name.trim(), character, photo, stride.roundToInt()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Uložit") }
    }
}
