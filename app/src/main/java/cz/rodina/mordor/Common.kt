package cz.rodina.mordor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun Avatar(photo: String?, name: String, size: Dp, border: Color, modifier: Modifier = Modifier) {
    val bmp = remember(photo) {
        photo?.let {
            runCatching {
                val b = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ParchmentDark)
            .border(max(2f, size.value / 18f).dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1).uppercase(), color = Ink, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

/** Glum na mapě: jen dvě svítící oči ve tmě, které občas mrknou. */
@Composable
fun GollumMarker(dp: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "blink")
    val open by t.animateFloat(
        initialValue = 1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3600
                1f at 0
                1f at 3250
                0.1f at 3380
                1f at 3600
            }
        ),
        label = "blink",
    )
    val glow by t.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
    Canvas(modifier.size(dp)) {
        val s = size.minDimension
        drawCircle(Color(0xFFD7F26B).copy(alpha = glow * 0.35f), s * 0.62f)
        drawCircle(Shadow)
        drawCircle(Color(0xFF6B7F3A), style = Stroke(s * 0.06f))
        val eye = Color(0xFFD7F26B)
        val r = s * 0.11f
        listOf(center + Offset(-s * 0.17f, -s * 0.04f), center + Offset(s * 0.17f, -s * 0.04f)).forEach { e ->
            val eh = r * 2 * open
            drawOval(eye, topLeft = Offset(e.x - r, e.y - eh / 2), size = Size(r * 2, eh))
            if (open > 0.5f) drawCircle(Color.Black, r * 0.45f, e)
        }
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("❦  $text  ❦", style = MaterialTheme.typography.headlineSmall, color = Ink, textAlign = TextAlign.Center)
        HorizontalDivider(Modifier.padding(top = 6.dp).fillMaxWidth(0.6f), color = Gold, thickness = 1.dp)
    }
}

@Composable
fun ScrollCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7EBD0), contentColor = Ink),
        border = androidx.compose.foundation.BorderStroke(1.dp, InkSoft.copy(alpha = 0.5f)),
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

/** Fotka → čtverec 200 px JPEG v base64 (vejde se do Firestore). */
suspend fun encodePhoto(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val m = min(info.size.width, info.size.height)
            decoder.setTargetSampleSize(max(1, m / 400))
        }
        val s = min(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - s) / 2, (bmp.height - s) / 2, s, s)
        val small = Bitmap.createScaledBitmap(square, 200, 200, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 75, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
}

private val dayFmt = DateTimeFormatter.ofPattern("EEEE d. M.", Locale("cs", "CZ"))
fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "dnes"
        today.minusDays(1) -> "včera"
        else -> d.format(dayFmt)
    }
}
fun dayLabel(s: String): String = runCatching { dayLabel(LocalDate.parse(s)) }.getOrDefault(s)
