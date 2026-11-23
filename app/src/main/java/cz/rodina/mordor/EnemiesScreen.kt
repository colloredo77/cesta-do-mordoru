package cz.rodina.mordor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

enum class Enemy { GOLLUM, NAZGUL }

@Composable
fun EnemiesScreen(game: GameState, family: Family, forecast: Forecast?) {
    LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("Nepřátelé") }
        item {
            Text(
                "Nepřátelé se pohybují v noci. Kolik ujdou, záleží na skutečném počasí v místě, kde bydlíte (${family.city.ifBlank { "neznámé" }}). " +
                    "Když vás dostihnou, přijde trest – takže choďte!",
                color = InkSoft, style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { ScrollCard { EnemyCard(Enemy.GOLLUM, game, family, forecast) } }
        item { ScrollCard { EnemyCard(Enemy.NAZGUL, game, family, forecast) } }
    }
}

@Composable
fun EnemyCard(enemy: Enemy, game: GameState, family: Family, forecast: Forecast?) {
    val yesterday = LocalDate.now().minusDays(1).toString()
    val lastNight = game.days.firstOrNull { it.day == yesterday }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (enemy) {
            Enemy.GOLLUM -> {
                Header(
                    { GollumMarker(44.dp) }, "Glum",
                    "Plíží se za výpravou a hlídá toho, kdo je nejvíc vzadu.",
                )
                Section("Stav")
                val last = game.last
                if (last != null) Line("Nejblíž mu je ${last.member.name} – ${(last.km - game.gollumKm).km()}.")
                lastNight?.gollum?.let { Line("Minulou noc: +${Gollum.km(it.steps).km()} (${it.reason.lowercase()}).") }
                forecast?.let { Line("Dnes v noci asi +${Gollum.km(it.gollum.steps).km()} – ${it.gollum.reason.lowercase()}.", bold = true) }
                Line("Základní tempo: ${family.gollumBase.fmt()} kroků (${Gollum.km(family.gollumBase).km()}) za noc. Změníte v Nastavení.")
                Section("Počasí")
                Rule("🌧️", "Déšť", "rychlejší (liják ještě víc)")
                Rule("🌫️", "Mlha", "rychlejší – ideální na plížení")
                Rule("☀️", "Slunce", "pomalejší, při ostrém slunci se schovává")
                Rule("❄️", "Sníh", "pomalejší – zebou ho prsty")
                Rule("🔥", "Horko nad 25 °C", "pomalejší")
                Rule("🎲", "Náhoda", "každou noc ±15 %")
                Section("Když vás dostihne")
                Line("Kdo je nejvíc vzadu, vrátí se o ${Gollum.CATCH_PENALTY_KM.toInt()} km. Glum pak uteče o ${Gollum.ESCAPE_KM.toInt()} km zpět do stínů.")
            }
            Enemy.NAZGUL -> {
                val rb = game.ringBearer
                val (name, desc) = when (game.nazgulPhase) {
                    1 -> "Černý jezdec" to "Hledá Prsten v Kraji a pronásleduje Prstenonoše až k Brodu Bruinenu."
                    2 -> "Okřídlený Nazgûl" to "Vrátil se na křídlech nad Mrtvými bažinami a je rychlejší než dřív."
                    3 -> "Nazgûl – poražen" to "Prsten byl zničen a Nazgûlové se rozplynuli."
                    else -> "Nazgûl – zatím zmizel" to "Smetla ho povodeň u Brodu Bruinenu. Vrátí se, až Prstenonoš dojde k Mrtvým bažinám."
                }
                Header({ NazgulMarker(44.dp, winged = game.nazgulPhase == 2) }, name, desc)
                Section("Stav")
                Line(
                    if (rb != null) "Prstenonoš: ${rb.member.name}" + (if (rb.member.character == Nazgul.RING_BEARER) " (Frodo)." else " – nikdo nemá postavu Frodo, takže jde po tom, kdo je nejdál.")
                    else "Prstenonoš zatím není.",
                )
                if (game.nazgulActive && rb != null) Line("Vzdálenost od Prstenonoše: ${(rb.km - game.nazgulKm).km()}.")
                if (rb != null && rb.woundedDaysLeft > 0) Line("Zranění: ještě ${rb.woundedDaysLeft} ${dny(rb.woundedDaysLeft)} poloviční kroky.", bold = true)
                lastNight?.nazgul?.let { Line("Minulou noc: +${it.km.km()} (${it.reason.lowercase()}).") }
                if (game.nazgulActive) forecast?.let {
                    val k = it.nazgul.km * (if (game.nazgulPhase == 2) Nazgul.WINGED_SPEED else 1.0)
                    Line("Dnes v noci asi +${k.km()} – ${it.nazgul.reason.lowercase()}.", bold = true)
                }
                Line("Základní tempo: ${family.nazgulBase.km()} za noc (okřídlený o čtvrtinu rychlejší). Změníte v Nastavení.")
                Section("Počasí")
                Rule("☀️", "Jasné slunce", "výrazně pomalejší – oslepuje ho")
                Rule("⛅", "Polojasno", "pomalejší")
                Rule("☁️", "Zataženo", "rychlejší – ve tmě cítí Prsten")
                Rule("🌙", "Dlouhá noc (15 h a víc)", "rychlejší – v zimě je nebezpečnější")
                Rule("🌧️", "Déšť", "pomalejší – bojí se vody (liják ho skoro zastaví)")
                Rule("🎲", "Náhoda", "každou noc ±15 %")
                Section("Když vás dostihne")
                Line("Prstenonoše zasáhne morgulská čepel: ${Nazgul.WOUND_DAYS} dny se mu počítá jen polovina kroků. Jezdec pak odskočí o ${Nazgul.KNOCKBACK_KM.toInt()} km.")
                Section("Fáze")
                Line("1. Černý jezdec – od startu do Brodu Bruinenu. Kdo tam dojde, je v bezpečí: jezdce smete povodeň.")
                Line("2. Okřídlený Nazgûl – od Mrtvých bažin až k Hoře osudu.")
            }
        }
    }
}

private fun dny(n: Int) = when (n) { 1 -> "den"; in 2..4 -> "dny"; else -> "dní" }

@Composable
private fun Header(icon: @Composable () -> Unit, name: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

@Composable
private fun Section(title: String) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Blood, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = Gold.copy(alpha = 0.5f))
    }
}

@Composable
private fun Line(text: String, bold: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

@Composable
private fun Rule(icon: String, what: String, effect: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(icon, modifier = Modifier.width(30.dp))
        Text("$what: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        Text(effect, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
