package cz.rodina.mordor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

@Composable
fun StatsContent(game: GameState, colors: Map<String, Color>) {
    var period by remember { mutableIntStateOf(7) }
    val today = LocalDate.now()
    val allDays = game.days.map { it.day }
    val days = allDays.takeLast(period)
    val members = game.members

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30).forEach { p ->
                if (p == period) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("$p dní") }
                else OutlinedButton(onClick = { period = p }, modifier = Modifier.weight(1f)) { Text("$p dní") }
            }
        }

        // legenda
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            members.forEach { ms ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors[ms.member.id] ?: Gold, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(ms.member.name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        ScrollCard {
            Column {
                Text(if (period == 7) "Kroky za posledních 7 dní" else "Kroky za posledních 30 dní", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (days.isEmpty() || members.isEmpty()) {
                    Text("Zatím žádná data.", color = InkSoft)
                } else if (period == 7) {
                    BarChart(days, members, colors)
                } else {
                    LineChart(days, members, colors)
                }
            }
        }

        // žebříček týdne
        val week = allDays.takeLast(7)
        val ranking = members.map { ms -> ms to week.sumOf { ms.member.stepsOn(it) } }.sortedByDescending { it.second }
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Žebříček týdne", fontWeight = FontWeight.Bold)
                ranking.forEachIndexed { i, (ms, sum) ->
                    val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "   " }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(medal, modifier = Modifier.width(30.dp))
                        Avatar(ms.member.photo, ms.member.name, 28.dp, colors[ms.member.id] ?: Gold)
                        Spacer(Modifier.width(8.dp))
                        Text(ms.member.name, modifier = Modifier.weight(1f))
                        Text("${sum.fmt()} kroků", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // přehled po členech
        members.forEach { ms ->
            val values = days.map { ms.member.stepsOn(it) }
            val sum = values.sum()
            val avg = if (values.isEmpty()) 0L else (sum.toDouble() / values.size).roundToLong()
            val allValues = allDays.map { it to ms.member.stepsOn(it) }
            val best = allValues.maxByOrNull { it.second }
            ScrollCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(ms.member.photo, ms.member.name, 32.dp, colors[ms.member.id] ?: Gold)
                        Spacer(Modifier.width(8.dp))
                        Text(ms.member.name, fontWeight = FontWeight.Bold)
                    }
                    Text("Za $period dní: ${sum.fmt()} kroků · průměr ${avg.fmt()} denně")
                    if (best != null && best.second > 0) {
                        Text("Nejlepší den: ${best.second.fmt()} kroků (${dayLabel(best.first)})")
                    }
                    Text(
                        "Celkem od startu: ${ms.totalSteps.fmt()} kroků · ${ms.km.km()} na mapě",
                        style = MaterialTheme.typography.bodySmall, color = InkSoft,
                    )
                }
            }
        }
        if (today.toString() == days.lastOrNull()) {
            Text("Dnešek se ještě počítá.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
    }
}

private val shortDay = java.time.format.DateTimeFormatter.ofPattern("EEEEEE", Locale("cs", "CZ"))

@Composable
private fun BarChart(days: List<String>, members: List<MemberState>, colors: Map<String, Color>) {
    val tm = rememberTextMeasurer()
    val maxV = max(1L, days.maxOf { d -> members.maxOf { it.member.stepsOn(d) } })
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val labelH = 18.dp.toPx()
        val chartH = size.height - labelH
        val slot = size.width / days.size
        val gap = slot * 0.18f
        val barW = (slot - gap) / members.size
        // vodorovné čáry po 5 000
        var lineV = 5000L
        while (lineV < maxV) {
            val y = chartH - chartH * lineV / maxV
            drawLine(InkSoft.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            lineV += 5000
        }
        days.forEachIndexed { i, d ->
            val x0 = i * slot + gap / 2
            members.forEachIndexed { j, ms ->
                val v = ms.member.stepsOn(d)
                val h = chartH * v / maxV
                drawRect(colors[ms.member.id] ?: Gold, topLeft = Offset(x0 + j * barW, chartH - h), size = Size(barW * 0.9f, h))
            }
            val label = runCatching { LocalDate.parse(d).format(shortDay) }.getOrDefault("")
            val l = tm.measure(label, TextStyle(fontFamily = FontFamily.Serif, fontSize = 11.sp, color = Ink))
            drawText(l, topLeft = Offset(i * slot + (slot - l.size.width) / 2, chartH + 3.dp.toPx()))
        }
        val top = tm.measure("${maxV.fmt()}", TextStyle(fontSize = 9.sp, color = InkSoft))
        drawText(top, topLeft = Offset(0f, 0f))
    }
}

@Composable
private fun LineChart(days: List<String>, members: List<MemberState>, colors: Map<String, Color>) {
    val tm = rememberTextMeasurer()
    val maxV = max(1L, days.maxOf { d -> members.maxOf { it.member.stepsOn(d) } })
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val labelH = 18.dp.toPx()
        val chartH = size.height - labelH
        val stepX = if (days.size > 1) size.width / (days.size - 1) else size.width
        var lineV = 5000L
        while (lineV < maxV) {
            val y = chartH - chartH * lineV / maxV
            drawLine(InkSoft.copy(alpha = 0.2f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            lineV += 5000
        }
        members.forEach { ms ->
            val path = Path()
            days.forEachIndexed { i, d ->
                val p = Offset(i * stepX, chartH - chartH * ms.member.stepsOn(d) / maxV)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, colors[ms.member.id] ?: Gold, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // popisky: první, prostřední, poslední den
        listOf(0, days.size / 2, days.size - 1).distinct().forEach { i ->
            val label = runCatching { LocalDate.parse(days[i]).let { "${it.dayOfMonth}. ${it.monthValue}." } }.getOrDefault("")
            val l = tm.measure(label, TextStyle(fontFamily = FontFamily.Serif, fontSize = 11.sp, color = Ink))
            val x = (i * stepX - l.size.width / 2f).coerceIn(0f, size.width - l.size.width)
            drawText(l, topLeft = Offset(x, chartH + 3.dp.toPx()))
        }
        val top = tm.measure("${maxV.fmt()}", TextStyle(fontSize = 9.sp, color = InkSoft))
        drawText(top, topLeft = Offset(0f, 0f))
    }
}
