package cz.rodina.mordor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private val MAP_W = 1100.dp
private val MAP_H = 760.dp

@Composable
fun MapScreen(game: GameState, colors: Map<String, Color>) {
    val hs = rememberScrollState()
    val vs = rememberScrollState()
    val density = LocalDensity.current
    var centered by remember { mutableStateOf(false) }
    val leaderKm = game.leader?.km ?: 0.0

    LaunchedEffect(hs.maxValue, vs.maxValue) {
        if (!centered && hs.maxValue > 0) {
            val (x, y) = Route.positionAt(leaderKm)
            with(density) {
                hs.scrollTo(((MAP_W * x).toPx() - 180.dp.toPx()).toInt().coerceIn(0, hs.maxValue))
                vs.scrollTo(((MAP_H * y).toPx() - 220.dp.toPx()).toInt().coerceIn(0, vs.maxValue))
            }
            centered = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(vs).horizontalScroll(hs)) {
            Box(Modifier.size(MAP_W, MAP_H)) {
                MapCanvas(game)
                val g = Route.positionAt(game.gollumKm)
                GollumMarker(30.dp, Modifier.offset(MAP_W * g.first - 15.dp, MAP_H * g.second + 10.dp))
                game.members.forEachIndexed { i, ms ->
                    val (x, y) = Route.positionAt(ms.km)
                    Avatar(
                        ms.member.photo, ms.member.name, 38.dp, colors[ms.member.id] ?: Gold,
                        Modifier.offset(MAP_W * x - 19.dp + (i * 10).dp, MAP_H * y - 46.dp - (i * 8).dp),
                    )
                }
            }
        }
        StatusPanel(game)
    }
}

@Composable
private fun StatusPanel(game: GameState) {
    val leader = game.leader
    val last = game.last
    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScrollCard {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (leader != null) {
                    val next = Route.next(leader.km)
                    Text("V čele: ${leader.member.name} – ${Route.lastReached(leader.km).name}", fontWeight = FontWeight.Bold)
                    Text(
                        if (next != null) "Do zastávky ${next.name} zbývá ${(next.km - leader.km).km()} · celkem ${leader.km.km()} z ${Route.totalKm.km()}"
                        else "Cíl dosažen! Prsten je zničen.",
                        style = MaterialTheme.typography.bodySmall, color = InkSoft,
                    )
                }
                if (last != null) {
                    val gap = last.km - game.gollumKm
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GollumMarker(22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                gap < 3 -> "Glum je těsně za ${last.member.name}! Jen ${gap.km()}. Dnes v noci hrozí přepadení!"
                                gap < 10 -> "Glum se blíží – ${gap.km()} za ${last.member.name}."
                                else -> "Glum je ${gap.km()} za ${last.member.name}."
                            },
                            color = if (gap < 10) Blood else Ink,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapCanvas(game: GameState) {
    val tm = rememberTextMeasurer()
    val leaderKm = game.leader?.km ?: 0.0
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        fun p(x: Float, y: Float) = Offset(x * w, y * h)

        // okraj pergamenu
        drawRect(InkSoft, style = Stroke(3.dp.toPx()))
        drawRect(InkSoft, topLeft = Offset(8.dp.toPx(), 8.dp.toPx()), size = Size(w - 16.dp.toPx(), h - 16.dp.toPx()), style = Stroke(1.dp.toPx()))

        // Mordor – stín
        drawRect(Blood.copy(alpha = 0.07f), topLeft = p(.77f, .56f), size = Size(.20f * w, .32f * h))

        // řeky
        river(listOf(p(.155f, .12f), p(.15f, .25f), p(.16f, .33f), p(.15f, .45f), p(.14f, .58f)))
        river(listOf(p(.58f, .08f), p(.585f, .30f), p(.60f, .45f), p(.615f, .55f), p(.625f, .64f), p(.64f, .72f), p(.66f, .80f), p(.64f, .93f)))
        river(listOf(p(.47f, .17f), p(.465f, .27f), p(.44f, .36f), p(.41f, .50f)))

        // hory
        mountains(p(.04f, .14f), p(.06f, .46f), 9)
        mountains(p(.52f, .06f), p(.515f, .42f), 16)
        mountains(p(.38f, .79f), p(.62f, .74f), 11)
        mountains(p(.77f, .55f), p(.97f, .54f), 9)
        mountains(p(.765f, .61f), p(.765f, .87f), 10)
        mountains(p(.78f, .885f), p(.96f, .885f), 8)

        // lesy
        forest(.64f, .10f, .76f, .38f, 45, 1)
        forest(.53f, .55f, .57f, .62f, 10, 2)
        forest(.19f, .315f, .22f, .345f, 6, 3)
        forest(.55f, .44f, .58f, .50f, 8, 4)

        // názvy krajů
        val region = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, color = InkSoft.copy(alpha = 0.55f), letterSpacing = 4.sp)
        label(tm, "KRAJ", p(.07f, .20f), region)
        label(tm, "ERIADOR", p(.27f, .42f), region)
        label(tm, "TEMNÝ HVOZD", p(.64f, .06f), region)
        label(tm, "ROHAN", p(.47f, .64f), region)
        label(tm, "GONDOR", p(.52f, .84f), region)
        label(tm, "MORDOR", p(.84f, .79f), region.copy(color = Blood.copy(alpha = 0.6f)))
        val small = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 11.sp, color = InkSoft.copy(alpha = 0.8f))
        label(tm, "Mlžné hory", p(.53f, .16f), small)
        label(tm, "Anduina", p(.595f, .36f), small.copy(color = River))
        label(tm, "Brandyvína", p(.105f, .50f), small.copy(color = River))

        // trasa
        val pts = Route.points.map { p(it.x, it.y) }
        val all = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(all, Blood.copy(alpha = 0.55f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        val done = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            Route.points.drop(1).forEach { if (it.km <= leaderKm) lineTo(it.x * w, it.y * h) }
            val (lx, ly) = Route.positionAt(leaderKm.coerceAtLeast(0.0))
            lineTo(lx * w, ly * h)
        }
        drawPath(done, Blood, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))

        // zastávky
        Route.points.forEachIndexed { i, wp ->
            val c = p(wp.x, wp.y)
            val reached = wp.km <= leaderKm
            drawCircle(if (reached) Blood else Parchment, 5.dp.toPx(), c)
            drawCircle(Ink, 5.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
            val st = TextStyle(
                fontFamily = FontFamily.Serif, fontSize = 11.sp, color = Ink,
                fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
            )
            val dy = if (i % 2 == 0) 8.dp.toPx() else (-22).dp.toPx()
            label(tm, wp.name, Offset(c.x - 20.dp.toPx(), c.y + dy), st)
        }

        compass(p(.08f, .83f), 34.dp.toPx(), tm)
        label(tm, "Cesta do Mordoru", p(.18f, .90f), TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, color = Ink, fontStyle = FontStyle.Italic))
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    drawText(tm, text, topLeft = at, style = style)
}

private fun DrawScope.river(pts: List<Offset>) {
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val mid = Offset((a.x + b.x) / 2 + (if (i % 2 == 0) 8f else -8f), (a.y + b.y) / 2)
            quadraticTo(mid.x, mid.y, b.x, b.y)
        }
    }
    drawPath(path, River.copy(alpha = 0.7f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.mountains(a: Offset, b: Offset, n: Int) {
    val s = 9.dp.toPx()
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val jitter = if (i % 2 == 0) s * 0.6f else -s * 0.4f
        val dx = b.x - a.x
        val dy = b.y - a.y
        val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx)
        val c = Offset(a.x + dx * t + (if (vertical) jitter else 0f), a.y + dy * t + (if (vertical) 0f else jitter))
        val path = Path().apply {
            moveTo(c.x - s, c.y + s * 0.6f)
            lineTo(c.x, c.y - s * 0.8f)
            lineTo(c.x + s, c.y + s * 0.6f)
        }
        drawPath(path, Ink.copy(alpha = 0.75f), style = Stroke(1.6.dp.toPx()))
        drawLine(Ink.copy(alpha = 0.35f), Offset(c.x, c.y - s * 0.8f), Offset(c.x + s * 0.3f, c.y + s * 0.5f), 1.dp.toPx())
    }
}

private fun DrawScope.forest(x1: Float, y1: Float, x2: Float, y2: Float, n: Int, seed: Int) {
    val r = Random(seed)
    val w = size.width
    val h = size.height
    val rad = 4.dp.toPx()
    repeat(n) {
        val c = Offset((x1 + (x2 - x1) * r.nextFloat()) * w, (y1 + (y2 - y1) * r.nextFloat()) * h)
        drawLine(Elf.copy(alpha = 0.7f), c, c + Offset(0f, rad * 1.6f), 1.dp.toPx())
        drawCircle(Elf.copy(alpha = 0.55f), rad, c)
    }
}

private fun DrawScope.compass(c: Offset, r: Float, tm: TextMeasurer) {
    drawCircle(Ink, r, c, style = Stroke(1.5.dp.toPx()))
    drawCircle(Ink, r * 0.15f, c)
    val star = Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.18f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.18f, c.y); close()
        moveTo(c.x - r, c.y); lineTo(c.x, c.y - r * 0.18f); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r * 0.18f); close()
    }
    drawPath(star, Ink.copy(alpha = 0.8f))
    val st = TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, color = Blood, fontWeight = FontWeight.Bold)
    drawText(tm, "S", topLeft = Offset(c.x - 4.dp.toPx(), c.y - r - 18.dp.toPx()), style = st)
}
