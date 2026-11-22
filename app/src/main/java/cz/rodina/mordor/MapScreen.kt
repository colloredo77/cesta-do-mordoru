package cz.rodina.mordor

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val MAP_W = 1400.dp
private val MAP_H = 960.dp
private const val MAX_SCALE = 2.2f

private val SeaLight = Color(0xFF8FB1B0)
private val SeaDeep = Color(0xFF55807F)
private val RiverDark = Color(0xFF3F6475)
private val MountLight = Color(0xFFDCC89C)
private val MountDark = Color(0xFF8A7250)
private val MordorLight = Color(0xFF6E5B4D)
private val MordorDark = Color(0xFF34291F)
private val TreeDark = Color(0xFF2F4A2A)
private val TreeMid = Color(0xFF4C6B3A)
private val TreeLight = Color(0xFF7C9A55)
private val GoldTree = Color(0xFFD4A62A)
private val Ash = Color(0xFF3B312B)
private val FogColor = Color(0xFFEFE8D8)
private val Road = Color(0xFF8A6A3E)

/** Body nad mlhou se popisují nahoře místo dole (aby se popisky nepřekrývaly). */
private val labelAbove = setOf("Rádovsko", "Mohylové vrchy", "Větrov", "Brod Bruinenu", "Morie", "Amon Hen", "Mrtvé bažiny", "Henneth Annûn", "Křižovatka", "Kirith Ungol")

private fun DrawScope.n(x: Float, y: Float) = Offset(x * size.width, y * size.height)

@Composable
fun MapScreen(game: GameState, colors: Map<String, Color>) {
    val density = LocalDensity.current
    val mapW = with(density) { MAP_W.toPx() }
    val mapH = with(density) { MAP_H.toPx() }
    var view by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(0.9f) }
    var shift by remember { mutableStateOf(Offset.Zero) }
    var centered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val leaderKm = game.leader?.km ?: 0.0
    val revealKm = Route.next(leaderKm)?.km ?: Route.totalKm
    val nextKm = Route.next(leaderKm)?.km

    fun minScale(): Float = if (view.width == 0) 0.3f else max(view.width / mapW, view.height / mapH)
    fun clamp(o: Offset, s: Float): Offset {
        val minX = view.width - mapW * s
        val minY = view.height - mapH * s
        return Offset(
            if (minX >= 0f) minX / 2f else o.x.coerceIn(minX, 0f),
            if (minY >= 0f) minY / 2f else o.y.coerceIn(minY, 0f),
        )
    }
    fun centerOn(km: Double, s: Float): Offset {
        val (x, y) = Route.positionAt(km)
        return clamp(Offset(view.width / 2f - x * mapW * s, view.height / 2f - y * mapH * s), s)
    }
    fun zoomBy(f: Float) {
        val c = Offset(view.width / 2f, view.height / 2f)
        val ns = (scale * f).coerceIn(minScale(), MAX_SCALE)
        shift = clamp(c - (c - shift) * (ns / scale), ns)
        scale = ns
    }

    LaunchedEffect(view) {
        if (!centered && view.width > 0) {
            scale = scale.coerceIn(minScale(), MAX_SCALE)
            shift = centerOn(leaderKm, scale)
            centered = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(Leather)
                .onSizeChanged { view = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(minScale(), MAX_SCALE)
                        shift = clamp(centroid - (centroid - shift) * (ns / scale) + pan, ns)
                        scale = ns
                    }
                },
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(MAP_W, MAP_H)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                        translationX = shift.x
                        translationY = shift.y
                    },
            ) {
                BaseMap(leaderKm)
                AnimatedLayer(nextKm)
                FogLayer(revealKm)
                DecorLayer()
            }

            // Žetony postav v souřadnicích obrazovky (nezvětšují se se zoomem)
            fun screen(km: Double): Offset {
                val (x, y) = Route.positionAt(km)
                return Offset(x * mapW * scale + shift.x, y * mapH * scale + shift.y)
            }
            val gp = screen(game.gollumKm)
            GollumMarker(
                34.dp,
                Modifier
                    .offset { IntOffset(gp.x.roundToInt(), gp.y.roundToInt() + 22.dp.roundToPx()) }
                    .anchor(0.5f, 0.5f),
            )
            val placed = mutableListOf<Offset>()
            game.members.sortedBy { it.km }.forEachIndexed { i, ms ->
                var p = screen(ms.km)
                val near = placed.count { (it - p).getDistance() < with(density) { 30.dp.toPx() } }
                p += Offset(with(density) { (near * 40).dp.toPx() }, 0f)
                placed += screen(ms.km)
                MemberToken(
                    ms, colors[ms.member.id] ?: Gold, i,
                    Modifier.offset { IntOffset(p.x.roundToInt(), p.y.roundToInt()) }.anchor(0.5f, 1f),
                )
            }

            Column(Modifier.align(Alignment.TopEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapButton("+") { zoomBy(1.4f) }
                MapButton("−") { zoomBy(1 / 1.4f) }
                MapButton("◎") {
                    scope.launch {
                        val from = shift
                        val to = centerOn(leaderKm, scale)
                        animate(0f, 1f, animationSpec = tween(700, easing = FastOutSlowInEasing)) { v, _ -> shift = lerp(from, to, v) }
                    }
                }
            }
        }
        StatusPanel(game)
    }
}

/** Umístí prvek tak, aby bod (fx, fy) jeho velikosti ležel v místě offsetu. */
private fun Modifier.anchor(fx: Float, fy: Float) = layout { m, c ->
    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
    layout(p.width, p.height) { p.place(-(p.width * fx).roundToInt(), -(p.height * fy).roundToInt()) }
}

@Composable
private fun MapButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .background(Leather.copy(alpha = 0.88f), CircleShape)
            .border(1.5.dp, GoldLight, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = GoldLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MemberToken(ms: MemberState, color: Color, phase: Int, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "bob")
    val bob by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1300 + phase * 170, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Column(
        modifier.offset { IntOffset(0, -(bob * 5.dp.toPx()).roundToInt()) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            ms.member.name,
            color = Parchment, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Leather.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                .border(1.dp, color, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
        Spacer(Modifier.size(2.dp))
        Avatar(ms.member.photo, ms.member.name, 44.dp, color)
        Canvas(Modifier.size(14.dp, 9.dp)) {
            val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }
            drawPath(p, color)
        }
    }
}

@Composable
private fun StatusPanel(game: GameState) {
    val leader = game.leader
    val last = game.last
    Column(
        Modifier.fillMaxWidth().background(Leather).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (leader != null) {
            val next = Route.next(leader.km)
            val pct = (leader.km / Route.totalKm * 100).roundToInt()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Den ${game.days.size} · ${leader.member.name} – ${Route.lastReached(leader.km).name} · $pct %",
                    color = GoldLight, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                    modifier = Modifier.weight(1f),
                )
                Text("v${BuildConfig.VERSION_CODE}", color = Parchment.copy(alpha = 0.6f), fontSize = 11.sp)
            }
            Text(
                if (next != null) "Další cíl: ${next.name} za ${(next.km - leader.km).km()}" else "Cíl dosažen! Prsten je zničen.",
                color = Parchment, fontSize = 13.sp, fontFamily = FontFamily.Serif,
            )
        }
        if (last != null) {
            val gap = last.km - game.gollumKm
            Row(verticalAlignment = Alignment.CenterVertically) {
                GollumMarker(22.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        gap < 3 -> "Glum je těsně za ${last.member.name}! Jen ${gap.km()}!"
                        gap < 10 -> "Glum se blíží – ${gap.km()} za ${last.member.name}."
                        else -> "Glum je ${gap.km()} za ${last.member.name}."
                    },
                    color = if (gap < 10) Color(0xFFFF8A65) else Parchment,
                    fontSize = 13.sp, fontFamily = FontFamily.Serif,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- statická mapa

@Composable
private fun BaseMap(leaderKm: Double) {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // pergamen
        drawRect(Brush.radialGradient(listOf(Color(0xFFF5E8C8), Color(0xFFE8D2A2), Color(0xFFC9A76A)), center = center, radius = size.maxDimension * 0.72f))
        val r = Random(7)
        repeat(650) {
            drawCircle(
                Color(0xFF7A5A30).copy(alpha = r.nextFloat() * 0.05f),
                radius = (2 + r.nextFloat() * 16).dp.toPx(),
                center = Offset(r.nextFloat() * w, r.nextFloat() * h),
            )
        }

        // moře a jezera
        sea(westCoast(), SeaDeep, SeaLight)
        sea(southCoast(), SeaDeep, SeaLight)
        lake(n(.885f, .40f), .05f * w, .045f * h)   // Rhûnské moře
        lake(n(.90f, .82f), .035f * w, .025f * h)   // Núrnen
        lake(n(.62f, .615f), .007f * w, .022f * h)  // Nen Hithoel

        // barvy krajin
        tint(.12f, .30f, .075f, Color(0xFF7FA35A), .40f)  // Kraj
        tint(.30f, .38f, .15f, Color(0xFFB9B07A), .25f)   // Eriador
        tint(.47f, .65f, .11f, Color(0xFFC2BC5C), .38f)   // Rohan
        tint(.55f, .85f, .10f, Color(0xFF6F9555), .32f)   // Gondor
        tint(.70f, .71f, .045f, Color(0xFF6F9555), .45f)  // Ithilien
        tint(.565f, .47f, .035f, Color(0xFFE0B83A), .55f) // Lórien
        tint(.66f, .52f, .06f, Color(0xFF9C7A4A), .30f)   // Hnědé pláně
        tint(.70f, .30f, .14f, Color(0xFF6E8A4E), .22f)   // Rhovanion

        // Mordor
        val mordor = Path().apply {
            val pts = listOf(n(.765f, .56f), n(.87f, .545f), n(.975f, .54f), n(.975f, .895f), n(.87f, .895f), n(.77f, .885f), n(.76f, .72f))
            moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
        }
        drawPath(mordor, Brush.radialGradient(listOf(Color(0xFF6A2A16), Ash, Color(0xFF241C18)), center = n(.86f, .70f), radius = .16f * w), alpha = 0.82f)
        val rm = Random(13)
        repeat(160) {
            val c = Offset((.78f + rm.nextFloat() * .19f) * w, (.57f + rm.nextFloat() * .31f) * h)
            drawCircle(Color.Black.copy(alpha = 0.12f), (1 + rm.nextFloat() * 3).dp.toPx(), c)
        }
        lavaCracks()

        // řeky
        river(listOf(n(.155f, .10f), n(.15f, .25f), n(.16f, .33f), n(.15f, .45f), n(.14f, .58f), n(.13f, .70f)))
        river(listOf(n(.58f, .07f), n(.585f, .30f), n(.60f, .45f), n(.615f, .55f), n(.62f, .60f), n(.625f, .64f), n(.64f, .72f), n(.66f, .80f), n(.64f, .935f)))
        river(listOf(n(.47f, .17f), n(.465f, .27f), n(.44f, .36f), n(.41f, .50f), n(.38f, .62f)))
        river(listOf(n(.56f, .58f), n(.585f, .63f), n(.615f, .66f)))

        // kopce, skály, bažiny
        hills(.215f, .275f, .25f, .31f, 9, 21)
        hills(.315f, .25f, .35f, .29f, 7, 22)
        hills(.40f, .58f, .45f, .62f, 6, 23)
        rocks(.64f, .595f, .68f, .645f, 14, 31)
        marsh(.685f, .585f, .725f, .625f, 41)

        // cesta
        val pts = Route.points.map { n(it.x, it.y) }
        val road = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(road, Road.copy(alpha = 0.35f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(road, Road, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))))
        val done = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            Route.points.drop(1).forEach { if (it.km <= leaderKm) lineTo(it.x * w, it.y * h) }
            val (lx, ly) = Route.positionAt(leaderKm.coerceAtLeast(0.0))
            lineTo(lx * w, ly * h)
        }
        drawPath(done, GoldLight.copy(alpha = 0.35f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(done, Color(0xFFC8921C), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // lesy (od severu k jihu, ať se překrývají správně)
        val trees = mutableListOf<Triple<Offset, Float, Int>>()
        fun forest(x1: Float, y1: Float, x2: Float, y2: Float, count: Int, seed: Int, kind: Int, rad: Float) {
            val fr = Random(seed)
            repeat(count) {
                val c = Offset((x1 + (x2 - x1) * fr.nextFloat()) * w, (y1 + (y2 - y1) * fr.nextFloat()) * h)
                trees += Triple(c, rad * (0.75f + fr.nextFloat() * 0.5f), kind)
            }
        }
        val tr = 5.5.dp.toPx()
        forest(.185f, .315f, .215f, .345f, 12, 1, 0, tr)     // Starý hvozd
        forest(.64f, .09f, .78f, .40f, 170, 2, 0, tr * 0.95f) // Temný hvozd
        forest(.548f, .45f, .582f, .495f, 16, 3, 2, tr)      // Lórien
        forest(.52f, .54f, .56f, .61f, 24, 4, 0, tr)          // Fangorn
        forest(.425f, .225f, .455f, .255f, 9, 5, 1, tr)       // Trollí hvozdy
        forest(.685f, .655f, .72f, .735f, 14, 6, 0, tr)       // Ithilien
        forest(.272f, .262f, .292f, .282f, 6, 7, 1, tr)       // Chetský les
        forest(.08f, .26f, .16f, .36f, 10, 8, 0, tr * 0.8f)   // stromy v Kraji
        trees.sortBy { it.first.y }
        trees.forEach { (c, rr, kind) -> if (kind == 1) pine(c, rr) else tree(c, rr, kind == 2) }

        // hory (sever → jih)
        val peaks = mutableListOf<Peak>()
        peaks += range(n(.055f, .10f), n(.07f, .46f), 15, 10.dp.toPx(), 51, snow = false, dark = false)
        peaks += range(n(.52f, .045f), n(.515f, .425f), 32, 13.dp.toPx(), 52, snow = true, dark = false)
        peaks += range(n(.37f, .80f), n(.62f, .755f), 24, 11.dp.toPx(), 53, snow = true, dark = false)
        peaks += range(n(.775f, .545f), n(.97f, .535f), 18, 11.dp.toPx(), 54, snow = false, dark = true)
        peaks += range(n(.765f, .60f), n(.765f, .875f), 18, 11.dp.toPx(), 55, snow = false, dark = true)
        peaks += range(n(.78f, .892f), n(.965f, .892f), 15, 10.dp.toPx(), 56, snow = false, dark = true)
        peaks.sortBy { it.c.y }
        peaks.forEach { peak(it) }

        volcano(n(.86f, .70f), 26.dp.toPx())

        // názvy krajů
        val region = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, color = Color(0xFF5A4126).copy(alpha = 0.55f), letterSpacing = 5.sp)
        text(tm, "KRAJ", n(.075f, .215f), region)
        text(tm, "ERIADOR", n(.27f, .43f), region)
        text(tm, "TEMNÝ HVOZD", n(.645f, .03f), region)
        text(tm, "ROHAN", n(.45f, .665f), region)
        text(tm, "GONDOR", n(.50f, .845f), region)
        text(tm, "MORDOR", n(.835f, .80f), region.copy(color = Color(0xFFE07040).copy(alpha = 0.75f)))
        val small = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 12.sp, color = Color(0xFF5A4126).copy(alpha = 0.8f))
        text(tm, "Mlžné hory", n(.528f, .20f), small)
        text(tm, "Bílé hory", n(.44f, .735f), small)
        text(tm, "Anduina", n(.597f, .36f), small.copy(color = RiverDark))
        text(tm, "Brandyvína", n(.10f, .52f), small.copy(color = RiverDark))
        text(tm, "Velké moře", n(.003f, .62f), small.copy(color = Color(0xFFEFF6F5)))

        // zastávky
        Route.points.forEach { wp ->
            val c = n(wp.x, wp.y)
            val reached = wp.km <= leaderKm
            if (reached) flag(c) else stone(c)
            plaque(tm, wp.name, c, above = wp.name in labelAbove, reached = reached)
        }
    }
}

// ---------------------------------------------------------------- animace

@Composable
private fun AnimatedLayer(nextKm: Double?) {
    val t = rememberInfiniteTransition(label = "map")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "pulse")
    val glow by t.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "glow")
    val smoke by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "smoke")
    Canvas(Modifier.fillMaxSize()) {
        val crater = n(.86f, .70f) + Offset(0f, -26.dp.toPx())
        val gr = 70.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF6A1A).copy(alpha = glow), Color(0x00FF3000)), center = crater, radius = gr), gr, crater)
        for (k in 0 until 4) {
            val p = (smoke + k / 4f) % 1f
            val drift = sin((p * 2 * PI + k).toFloat()) * 10.dp.toPx()
            val c = crater + Offset(drift + p * 18.dp.toPx(), -p * 80.dp.toPx())
            drawCircle(Color(0xFF2E2622).copy(alpha = (1 - p) * 0.5f), (6 + p * 18).dp.toPx(), c)
        }
        if (nextKm != null) {
            val wp = Route.points.firstOrNull { it.km == nextKm }
            if (wp != null) {
                val c = n(wp.x, wp.y)
                drawCircle(GoldLight.copy(alpha = 1f - pulse), (8 + pulse * 26).dp.toPx(), c, style = Stroke(3.dp.toPx()))
                drawCircle(GoldLight.copy(alpha = (1f - pulse) * 0.6f), (8 + pulse * 14).dp.toPx(), c, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

// ---------------------------------------------------------------- mlha (neprozkoumané kraje)

@Composable
private fun FogLayer(revealKm: Double) {
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val w = size.width
            val h = size.height
            val pts = mutableListOf<Offset>()
            Route.points.filter { it.km <= revealKm }.forEach { pts += Offset(it.x * w, it.y * h) }
            val radius = 0.085f * w
            val cols = 46
            val rows = 32
            val cw = w / cols
            val ch = h / rows
            val rnd = Random(99)
            val puffs = mutableListOf<Pair<Offset, Float>>()
            for (i in 0..cols) for (j in 0..rows) {
                val c = Offset(i * cw + (rnd.nextFloat() - 0.5f) * cw * 0.6f, j * ch + (rnd.nextFloat() - 0.5f) * ch * 0.6f)
                val d = distanceToPath(c, pts)
                val a = ((d - radius) / (0.45f * radius)).coerceIn(0f, 1f) * (0.88f + rnd.nextFloat() * 0.1f)
                if (a > 0.02f) puffs += c to a
            }
            val pr = cw * 1.35f
            val brushes = puffs.map { (c, a) ->
                Brush.radialGradient(listOf(FogColor.copy(alpha = a), FogColor.copy(alpha = a * 0.7f), Color.Transparent), center = c, radius = pr)
            }
            onDrawBehind {
                puffs.forEachIndexed { k, (c, _) -> drawCircle(brushes[k], pr, c) }
            }
        },
    )
}

private fun distanceToPath(p: Offset, pts: List<Offset>): Float {
    if (pts.isEmpty()) return Float.MAX_VALUE
    if (pts.size == 1) return (p - pts[0]).getDistance()
    var best = Float.MAX_VALUE
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        val t = if (len2 == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
        val q = a + ab * t
        best = minOf(best, hypot(p.x - q.x, p.y - q.y))
    }
    return best
}

// ---------------------------------------------------------------- rám, růžice, kartuš

@Composable
private fun DecorLayer() {
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val o = 6.dp.toPx()
        drawRect(Color(0xFF4A3420), style = Stroke(o * 2))
        drawRect(GoldLight, topLeft = Offset(o * 1.6f, o * 1.6f), size = Size(w - o * 3.2f, h - o * 3.2f), style = Stroke(1.5.dp.toPx()))
        drawRect(Color(0xFF4A3420), topLeft = Offset(o * 2.4f, o * 2.4f), size = Size(w - o * 4.8f, h - o * 4.8f), style = Stroke(1.dp.toPx()))
        listOf(Offset(o * 2f, o * 2f), Offset(w - o * 2f, o * 2f), Offset(o * 2f, h - o * 2f), Offset(w - o * 2f, h - o * 2f)).forEach { c ->
            val s = 9.dp.toPx()
            val d = Path().apply { moveTo(c.x, c.y - s); lineTo(c.x + s, c.y); lineTo(c.x, c.y + s); lineTo(c.x - s, c.y); close() }
            drawPath(d, GoldLight)
            drawPath(d, Color(0xFF4A3420), style = Stroke(1.dp.toPx()))
        }

        compass(n(.935f, .13f), 46.dp.toPx(), tm)

        // kartuš s názvem
        val tl = n(.035f, .865f)
        val cs = Size(.27f * w, .085f * h)
        drawRoundRect(Color(0xFFF3E2BA), tl, cs, CornerRadius(10.dp.toPx()))
        drawRoundRect(Color(0xFF4A3420), tl, cs, CornerRadius(10.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawRoundRect(GoldLight, tl + Offset(4.dp.toPx(), 4.dp.toPx()), Size(cs.width - 8.dp.toPx(), cs.height - 8.dp.toPx()), CornerRadius(7.dp.toPx()), style = Stroke(1.dp.toPx()))
        val title = tm.measure("Cesta do Mordoru", TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 26.sp, color = Ink, fontWeight = FontWeight.Bold))
        drawText(title, topLeft = Offset(tl.x + (cs.width - title.size.width) / 2, tl.y + 8.dp.toPx()))
        val sub = tm.measure("mapa Společenstva", TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = InkSoft))
        drawText(sub, topLeft = Offset(tl.x + (cs.width - sub.size.width) / 2, tl.y + 8.dp.toPx() + title.size.height))
    }
}

// ---------------------------------------------------------------- kreslicí pomocníci

private fun DrawScope.text(tm: TextMeasurer, s: String, at: Offset, style: TextStyle) = drawText(tm, s, topLeft = at, style = style)

private fun DrawScope.tint(x: Float, y: Float, r: Float, color: Color, alpha: Float) {
    val c = n(x, y)
    val rad = r * size.width
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center = c, radius = rad), rad, c)
}

private fun DrawScope.westCoast(): Path = Path().apply {
    moveTo(0f, 0f)
    for (i in 0..24) {
        val y = i / 24f
        val x = 0.034f + 0.014f * sin(i * 1.7f) + 0.006f * cos(i * 3.1f)
        lineTo(x * size.width, y * size.height)
    }
    lineTo(0f, size.height); close()
}

private fun DrawScope.southCoast(): Path = Path().apply {
    moveTo(.26f * size.width, size.height)
    for (i in 0..16) {
        val x = .26f + (.62f - .26f) * i / 16f
        val y = 0.955f - 0.025f * sin(i / 16f * PI.toFloat()) + 0.006f * sin(i * 2.3f)
        lineTo(x * size.width, y * size.height)
    }
    lineTo(.62f * size.width, size.height); close()
}

private fun DrawScope.sea(p: Path, deep: Color, light: Color) {
    drawPath(p, Brush.verticalGradient(listOf(light, deep)))
    drawPath(p, RiverDark.copy(alpha = 0.6f), style = Stroke(2.dp.toPx()))
    val r = Random(p.getBounds().left.toInt() + 5)
    val b = p.getBounds()
    repeat(18) {
        val c = Offset(b.left + r.nextFloat() * b.width * 0.8f, b.top + r.nextFloat() * b.height)
        if (p.getBounds().contains(c)) wave(c, 5.dp.toPx())
    }
}

private fun DrawScope.wave(c: Offset, s: Float) {
    val p = Path().apply {
        moveTo(c.x - s, c.y)
        quadraticTo(c.x - s / 2, c.y - s * 0.6f, c.x, c.y)
        quadraticTo(c.x + s / 2, c.y - s * 0.6f, c.x + s, c.y)
    }
    drawPath(p, Color.White.copy(alpha = 0.55f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.lake(c: Offset, rx: Float, ry: Float) {
    drawOval(Brush.radialGradient(listOf(SeaLight, SeaDeep), center = c, radius = max(rx, ry)), topLeft = c - Offset(rx, ry), size = Size(rx * 2, ry * 2))
    drawOval(RiverDark.copy(alpha = 0.6f), topLeft = c - Offset(rx, ry), size = Size(rx * 2, ry * 2), style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.river(pts: List<Offset>) {
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val mid = Offset((a.x + b.x) / 2 + (if (i % 2 == 0) 9f else -9f), (a.y + b.y) / 2)
            quadraticTo(mid.x, mid.y, b.x, b.y)
        }
    }
    drawPath(path, RiverDark.copy(alpha = 0.7f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, SeaLight, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
}

private data class Peak(val c: Offset, val s: Float, val snow: Boolean, val dark: Boolean)

private fun range(a: Offset, b: Offset, n: Int, size: Float, seed: Int, snow: Boolean, dark: Boolean): List<Peak> {
    val r = Random(seed)
    val dir = b - a
    val len = dir.getDistance()
    val perp = Offset(-dir.y / len, dir.x / len)
    return (0 until n).map { i ->
        val t = i / (n - 1f)
        val s = size * (0.75f + r.nextFloat() * 0.55f)
        val side = (r.nextFloat() - 0.5f) * size * 2.2f
        Peak(a + dir * t + perp * side, s, snow && s > size * 0.85f, dark)
    }
}

private fun DrawScope.peak(p: Peak) {
    val (c, s) = p.c to p.s
    val top = Offset(c.x, c.y - s * 1.15f)
    val left = Offset(c.x - s, c.y + s * 0.5f)
    val right = Offset(c.x + s, c.y + s * 0.5f)
    val mid = Offset(c.x + s * 0.12f, c.y + s * 0.5f)
    drawOval(Color.Black.copy(alpha = 0.10f), topLeft = Offset(c.x - s * 0.9f, c.y + s * 0.35f), size = Size(s * 2.2f, s * 0.4f))
    drawPath(Path().apply { moveTo(left.x, left.y); lineTo(top.x, top.y); lineTo(mid.x, mid.y); close() }, if (p.dark) MordorLight else MountLight)
    drawPath(Path().apply { moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(mid.x, mid.y); close() }, if (p.dark) MordorDark else MountDark)
    if (p.snow) {
        val snow = Path().apply {
            moveTo(top.x, top.y)
            lineTo(top.x - s * 0.36f, top.y + s * 0.42f)
            lineTo(top.x - s * 0.12f, top.y + s * 0.32f)
            lineTo(top.x + s * 0.04f, top.y + s * 0.46f)
            lineTo(top.x + s * 0.32f, top.y + s * 0.38f)
            close()
        }
        drawPath(snow, Color.White.copy(alpha = 0.92f))
    }
    val outline = Path().apply { moveTo(left.x, left.y); lineTo(top.x, top.y); lineTo(right.x, right.y) }
    drawPath(outline, Ink.copy(alpha = 0.85f), style = Stroke(1.3.dp.toPx(), join = StrokeJoin.Round))
    drawLine(Ink.copy(alpha = 0.3f), top, mid, 1.dp.toPx())
}

private fun DrawScope.volcano(base: Offset, s: Float) {
    val l = base + Offset(-s * 1.4f, 0f)
    val rr = base + Offset(s * 1.4f, 0f)
    val tl = base + Offset(-s * 0.28f, -s)
    val trr = base + Offset(s * 0.28f, -s)
    drawOval(Color.Black.copy(alpha = 0.25f), topLeft = base + Offset(-s * 1.5f, -s * 0.15f), size = Size(s * 3f, s * 0.4f))
    val cone = Path().apply { moveTo(l.x, l.y); lineTo(tl.x, tl.y); lineTo(trr.x, trr.y); lineTo(rr.x, rr.y); close() }
    drawPath(cone, Brush.horizontalGradient(listOf(Color(0xFF5E4436), Color(0xFF2A1F1A)), startX = l.x, endX = rr.x))
    drawPath(cone, Color.Black, style = Stroke(1.5.dp.toPx()))
    drawLine(Color(0xFFFF7A1A), base + Offset(-s * 0.15f, -s), base + Offset(-s * 0.55f, -s * 0.2f), 2.dp.toPx(), cap = StrokeCap.Round)
    drawLine(Color(0xFFFF9A2A), base + Offset(s * 0.1f, -s), base + Offset(s * 0.35f, -s * 0.45f), 1.6.dp.toPx(), cap = StrokeCap.Round)
    drawOval(Color(0xFFFF5A10), topLeft = base + Offset(-s * 0.26f, -s * 1.06f), size = Size(s * 0.52f, s * 0.14f))
}

private fun DrawScope.lavaCracks() {
    val r = Random(17)
    repeat(9) {
        var p = n(.80f + r.nextFloat() * .14f, .62f + r.nextFloat() * .22f)
        val path = Path().apply { moveTo(p.x, p.y) }
        repeat(4) {
            p += Offset((r.nextFloat() - 0.5f) * 24.dp.toPx(), (r.nextFloat() - 0.5f) * 16.dp.toPx())
            path.lineTo(p.x, p.y)
        }
        drawPath(path, Color(0xFFE8561A).copy(alpha = 0.45f), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun DrawScope.tree(c: Offset, r: Float, golden: Boolean) {
    drawOval(Color.Black.copy(alpha = 0.12f), topLeft = c + Offset(-r, r * 0.7f), size = Size(r * 2.2f, r * 0.7f))
    drawLine(Color(0xFF5A3D22), c, c + Offset(0f, r * 1.3f), r * 0.35f)
    drawCircle(if (golden) Color(0xFFA67C17) else TreeDark, r, c)
    drawCircle(if (golden) GoldTree else TreeMid, r * 0.75f, c + Offset(-r * 0.12f, -r * 0.12f))
    drawCircle(if (golden) Color(0xFFF2D36B) else TreeLight, r * 0.35f, c + Offset(-r * 0.35f, -r * 0.35f))
}

private fun DrawScope.pine(c: Offset, r: Float) {
    drawLine(Color(0xFF5A3D22), c + Offset(0f, r * 0.6f), c + Offset(0f, r * 1.4f), r * 0.3f)
    for (k in 0..1) {
        val y = c.y - r * 0.9f + k * r * 0.7f
        val p = Path().apply { moveTo(c.x, y); lineTo(c.x + r * (0.7f + k * 0.25f), y + r * 1.0f); lineTo(c.x - r * (0.7f + k * 0.25f), y + r * 1.0f); close() }
        drawPath(p, if (k == 0) TreeMid else TreeDark)
    }
}

private fun DrawScope.hills(x1: Float, y1: Float, x2: Float, y2: Float, count: Int, seed: Int) {
    val r = Random(seed)
    repeat(count) {
        val c = n(x1 + (x2 - x1) * r.nextFloat(), y1 + (y2 - y1) * r.nextFloat())
        val s = (6 + r.nextFloat() * 4).dp.toPx()
        val p = Path().apply { moveTo(c.x - s, c.y); quadraticTo(c.x, c.y - s * 1.3f, c.x + s, c.y); close() }
        drawPath(p, Color(0xFFB9A56E).copy(alpha = 0.8f))
        val o = Path().apply { moveTo(c.x - s, c.y); quadraticTo(c.x, c.y - s * 1.3f, c.x + s, c.y) }
        drawPath(o, Ink.copy(alpha = 0.7f), style = Stroke(1.2.dp.toPx()))
    }
}

private fun DrawScope.rocks(x1: Float, y1: Float, x2: Float, y2: Float, count: Int, seed: Int) {
    val r = Random(seed)
    repeat(count) {
        val c = n(x1 + (x2 - x1) * r.nextFloat(), y1 + (y2 - y1) * r.nextFloat())
        val s = (4 + r.nextFloat() * 4).dp.toPx()
        val p = Path().apply {
            moveTo(c.x - s, c.y + s * 0.4f); lineTo(c.x - s * 0.5f, c.y - s * 0.7f); lineTo(c.x + s * 0.2f, c.y - s * 0.4f)
            lineTo(c.x + s * 0.6f, c.y - s * 0.9f); lineTo(c.x + s, c.y + s * 0.4f); close()
        }
        drawPath(p, Color(0xFF8E8678))
        drawPath(p, Ink.copy(alpha = 0.75f), style = Stroke(1.dp.toPx()))
    }
}

private fun DrawScope.marsh(x1: Float, y1: Float, x2: Float, y2: Float, seed: Int) {
    val r = Random(seed)
    repeat(10) {
        val c = n(x1 + (x2 - x1) * r.nextFloat(), y1 + (y2 - y1) * r.nextFloat())
        val rx = (5 + r.nextFloat() * 6).dp.toPx()
        drawOval(Color(0xFF5E7A6A).copy(alpha = 0.6f), topLeft = c - Offset(rx, rx * 0.35f), size = Size(rx * 2, rx * 0.7f))
    }
    repeat(24) {
        val c = n(x1 + (x2 - x1) * r.nextFloat(), y1 + (y2 - y1) * r.nextFloat())
        val s = 5.dp.toPx()
        drawLine(Color(0xFF4E5A2E), c, c + Offset(-s * 0.3f, -s), 1.dp.toPx())
        drawLine(Color(0xFF4E5A2E), c, c + Offset(s * 0.3f, -s * 0.9f), 1.dp.toPx())
    }
}

private fun DrawScope.stone(c: Offset) {
    val r = 6.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.2f), r, c + Offset(1.5f, 2f))
    drawCircle(Color(0xFFB8AE9A), r, c)
    drawCircle(Ink, r, c, style = Stroke(1.5.dp.toPx()))
    drawCircle(Color(0xFF6E6658), r * 0.35f, c)
}

private fun DrawScope.flag(c: Offset) {
    val h = 22.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.2f), 4.dp.toPx(), c + Offset(1.5f, 2f))
    drawLine(Color(0xFF4A3420), c, c + Offset(0f, -h), 2.dp.toPx(), cap = StrokeCap.Round)
    val p = Path().apply {
        moveTo(c.x, c.y - h); lineTo(c.x + h * 0.7f, c.y - h * 0.82f); lineTo(c.x + h * 0.5f, c.y - h * 0.7f)
        lineTo(c.x + h * 0.7f, c.y - h * 0.56f); lineTo(c.x, c.y - h * 0.56f); close()
    }
    drawPath(p, Blood)
    drawPath(p, Color(0xFF4A1010), style = Stroke(1.dp.toPx()))
    drawCircle(GoldLight, 2.5.dp.toPx(), c + Offset(0f, -h))
}

private fun DrawScope.plaque(tm: TextMeasurer, s: String, c: Offset, above: Boolean, reached: Boolean) {
    val style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 12.sp, color = if (reached) Color(0xFF3A1A0A) else Ink, fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal)
    val l = tm.measure(s, style)
    val pad = 4.dp.toPx()
    val wdt = l.size.width + pad * 2
    val hgt = l.size.height + pad * 0.8f
    val gap = if (reached) 6.dp.toPx() else 10.dp.toPx()
    val top = if (above) c.y - (if (reached) 26.dp.toPx() else 10.dp.toPx()) - hgt - 2.dp.toPx() else c.y + gap
    val tl = Offset(c.x - wdt / 2, top)
    drawRoundRect(if (reached) Color(0xFFF6DE9A) else Color(0xFFF3E6C8).copy(alpha = 0.92f), tl, Size(wdt, hgt), CornerRadius(4.dp.toPx()))
    drawRoundRect(if (reached) Gold else InkSoft, tl, Size(wdt, hgt), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
    drawText(l, topLeft = tl + Offset(pad, pad * 0.4f))
}

private fun DrawScope.compass(c: Offset, r: Float, tm: TextMeasurer) {
    drawCircle(Color(0xFFF3E2BA).copy(alpha = 0.85f), r * 1.05f, c)
    drawCircle(Color(0xFF4A3420), r, c, style = Stroke(2.dp.toPx()))
    drawCircle(GoldLight, r * 0.9f, c, style = Stroke(1.dp.toPx()))
    for (k in 0 until 32) {
        val a = k / 32f * 2 * PI.toFloat()
        val len = if (k % 4 == 0) 0.12f else 0.06f
        drawLine(Color(0xFF4A3420), c + Offset(cos(a), sin(a)) * (r * 0.9f), c + Offset(cos(a), sin(a)) * (r * (0.9f - len)), 1.dp.toPx())
    }
    for (k in 0 until 8) {
        val a = k / 8f * 2 * PI.toFloat() - PI.toFloat() / 2
        val len = if (k % 2 == 0) r * 0.8f else r * 0.48f
        val wdt = r * 0.13f
        val tip = c + Offset(cos(a), sin(a)) * len
        val sideA = c + Offset(cos(a + PI.toFloat() / 2), sin(a + PI.toFloat() / 2)) * wdt
        val sideB = c + Offset(cos(a - PI.toFloat() / 2), sin(a - PI.toFloat() / 2)) * wdt
        drawPath(Path().apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(sideA.x, sideA.y); close() }, if (k == 0) Blood else Color(0xFF4A3420))
        drawPath(Path().apply { moveTo(c.x, c.y); lineTo(tip.x, tip.y); lineTo(sideB.x, sideB.y); close() }, if (k == 0) Color(0xFFC0392B) else Color(0xFFD9C49A))
    }
    drawCircle(GoldLight, r * 0.08f, c)
    val st = TextStyle(fontFamily = FontFamily.Serif, fontSize = 14.sp, color = Blood, fontWeight = FontWeight.Bold)
    val l = tm.measure("S", st)
    drawText(l, topLeft = Offset(c.x - l.size.width / 2f, c.y - r - l.size.height - 2.dp.toPx()))
}

@Suppress("unused")
private fun absf(v: Float) = abs(v)
