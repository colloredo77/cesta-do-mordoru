package cz.rodina.mordor

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.random.Random

object Gollum {
    const val STRIDE_M = 0.7
    const val HEAD_START_KM = 25.0
    const val CATCH_PENALTY_KM = 10.0
    const val ESCAPE_KM = 15.0

    /** Kolik kroků Glum ujde za den podle počasí. Slunce nesnáší, déšť a mlha mu svědčí. */
    fun stepsFor(day: String, base: Long, w: DayWeather?): GollumDay {
        var m = 1.0
        val reasons = mutableListOf<String>()
        if (w == null) {
            reasons += "bez zpráv o počasí – Glum jde svým tempem"
        } else {
            when {
                w.precip >= 5.0 -> { m *= 1.45; reasons += "liják – Glum se plíží rychle" }
                w.precip >= 1.0 -> { m *= 1.2; reasons += "déšť mu svědčí" }
            }
            if (w.code == 45 || w.code == 48) { m *= 1.3; reasons += "mlha – ideální na plížení" }
            when {
                w.sunshineH >= 6.0 -> { m *= 0.6; reasons += "ostré slunce – schovává se v jeskyni" }
                w.sunshineH >= 3.0 -> { m *= 0.8; reasons += "slunce ho pálí do očí" }
            }
            if (w.code in 71..77 || w.code in 85..86) { m *= 0.85; reasons += "sníh – zebou ho prsty" }
            if (w.tmax >= 25.0) { m *= 0.85; reasons += "horko" }
            if (reasons.isEmpty()) reasons += "zataženo – Glum jde vytrvale"
        }
        val rnd = Random(day.hashCode()).nextDouble(0.85, 1.15)
        m = (m * rnd).coerceIn(0.35, 1.9)
        val text = reasons.joinToString(", ").replaceFirstChar { it.uppercase() }
        return GollumDay((base * m).roundToLong(), text)
    }
}

enum class Kind { WAYPOINT, CAUGHT, FINISH, START }

data class Event(val day: String, val text: String, val kind: Kind)
data class DayLog(val day: String, val familySteps: Long, val gollum: GollumDay?)
data class MemberState(
    val member: Member,
    val km: Double,
    val todaySteps: Long,
    val totalSteps: Long,
    val caught: Int,
)
data class GameState(
    val members: List<MemberState>,
    val gollumKm: Double,
    val events: List<Event>,
    val days: List<DayLog>,
) {
    val last: MemberState? get() = members.filter { it.km < Route.totalKm }.minByOrNull { it.km }
    val leader: MemberState? get() = members.maxByOrNull { it.km }
}

/**
 * Přehraje celou cestu den po dni od startu. Vše se počítá z uložených kroků,
 * takže pozdě zadané kroky se promítnou zpětně.
 */
fun simulate(f: Family, ms: List<Member>, today: LocalDate): GameState {
    val total = Route.totalKm
    val start = runCatching { LocalDate.parse(f.startDate) }.getOrDefault(today)
    val pos = ms.associate { it.id to 0.0 }.toMutableMap()
    val caught = ms.associate { it.id to 0 }.toMutableMap()
    val steps = ms.associate { it.id to 0L }.toMutableMap()
    val events = mutableListOf(Event(f.startDate, "Společenstvo vyráží z Hobitína. Glum je ${Gollum.HEAD_START_KM.toInt()} km za vámi.", Kind.START))
    val logs = mutableListOf<DayLog>()
    var g = -Gollum.HEAD_START_KM

    var d = start
    while (!d.isAfter(today)) {
        val key = d.toString()
        var familySteps = 0L
        for (m in ms) {
            val s = m.stepsOn(key)
            familySteps += s
            steps[m.id] = steps.getValue(m.id) + s
            val before = pos.getValue(m.id)
            if (before >= total) continue
            val after = (before + s * m.strideCm / 100_000.0).coerceAtMost(total)
            pos[m.id] = after
            Route.points.filter { it.km > before && it.km <= after }.forEach { w ->
                val finish = w.km >= total
                events += Event(key, "${m.name}: ${w.name}. ${w.note}", if (finish) Kind.FINISH else Kind.WAYPOINT)
            }
        }
        var gd: GollumDay? = null
        if (d.isBefore(today)) {
            gd = f.gollumDays[key]
            if (gd != null) {
                g += gd.steps * Gollum.STRIDE_M / 1000.0
                for (m in ms.sortedBy { pos.getValue(it.id) }) {
                    val p = pos.getValue(m.id)
                    if (p < total && g >= p && g > 0.0) {
                        pos[m.id] = max(0.0, p - Gollum.CATCH_PENALTY_KM)
                        caught[m.id] = caught.getValue(m.id) + 1
                        g -= Gollum.ESCAPE_KM
                        events += Event(
                            key,
                            "Glum dostihl: ${m.name}! Návrat o ${Gollum.CATCH_PENALTY_KM.toInt()} km. Glum se se syčením stáhl do stínů.",
                            Kind.CAUGHT,
                        )
                    }
                }
            }
        }
        logs += DayLog(key, familySteps, gd)
        d = d.plusDays(1)
    }

    val todayKey = today.toString()
    val states = ms.map {
        MemberState(it, pos.getValue(it.id), it.stepsOn(todayKey), steps.getValue(it.id), caught.getValue(it.id))
    }
    return GameState(states, g, events, logs)
}
