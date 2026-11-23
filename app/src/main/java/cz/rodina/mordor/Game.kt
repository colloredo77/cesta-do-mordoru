package cz.rodina.mordor

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.random.Random

object Gollum {
    const val STRIDE_M = 0.7
    const val HEAD_START_KM = 25.0
    const val CATCH_PENALTY_KM = 10.0
    const val ESCAPE_KM = 15.0

    /** Kolik kroků Glum ujde za noc podle počasí. Slunce nesnáší, déšť a mlha mu svědčí. */
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

    fun km(steps: Long) = steps * STRIDE_M / 1000.0
}

object Nazgul {
    const val START_KM = -60.0
    const val FORD_KM = 640.0       // Brod Bruinenu
    const val MARSH_KM = 2120.0     // Mrtvé bažiny
    const val WINGED_GAP_KM = 50.0
    const val WINGED_SPEED = 1.25
    const val KNOCKBACK_KM = 50.0
    const val WOUND_DAYS = 3
    const val RING_BEARER = "Frodo Pytlík"

    /** Kolik km ujede jezdec za noc. Slunce ho oslepuje, tma a mraky mu pomáhají, vody se bojí. */
    fun kmFor(day: String, baseKm: Double, w: DayWeather?): EnemyDay {
        var m = 1.0
        val r = mutableListOf<String>()
        if (w == null) {
            r += "bez zpráv o počasí – jede svým tempem"
        } else {
            val ratio = if (w.daylightH > 0.1) w.sunshineH / w.daylightH else 0.0
            when {
                ratio >= 0.6 -> { m *= 0.55; r += "jasné slunce ho oslepuje" }
                ratio >= 0.3 -> { m *= 0.8; r += "polojasno – slunce ho trápí" }
                ratio < 0.1 -> { m *= 1.3; r += "zataženo – cítí Prsten" }
            }
            val night = 24.0 - w.daylightH
            when {
                night >= 15.0 -> { m *= 1.2; r += "dlouhá zimní noc" }
                night <= 9.0 -> { m *= 0.85; r += "krátká noc" }
            }
            when {
                w.precip >= 5.0 -> { m *= 0.5; r += "liják – bojí se vody" }
                w.precip >= 1.0 -> { m *= 0.75; r += "déšť ho zdržuje" }
            }
            if (r.isEmpty()) r += "šero – jezdec jede svým tempem"
        }
        val rnd = Random("nazgul$day".hashCode()).nextDouble(0.85, 1.15)
        m = (m * rnd).coerceIn(0.3, 1.9)
        return EnemyDay(baseKm * m, r.joinToString(", ").replaceFirstChar { it.uppercase() })
    }
}

enum class Kind { WAYPOINT, CAUGHT, FINISH, START, ENEMY }

data class Event(val day: String, val text: String, val kind: Kind)
data class DayLog(val day: String, val familySteps: Long, val gollum: GollumDay?, val nazgul: EnemyDay?)
data class MemberState(
    val member: Member,
    val km: Double,
    val todaySteps: Long,
    val totalSteps: Long,
    val caught: Int,
    val woundedDaysLeft: Int = 0,
)

/** 0 = zmizel u Brodu, 1 = Černý jezdec, 2 = okřídlený Nazgûl, 3 = poražen (Prsten zničen) */
data class GameState(
    val members: List<MemberState>,
    val gollumKm: Double,
    val events: List<Event>,
    val days: List<DayLog>,
    val nazgulKm: Double = 0.0,
    val nazgulPhase: Int = 1,
    val ringBearerId: String? = null,
) {
    val last: MemberState? get() = members.filter { it.km < Route.totalKm }.minByOrNull { it.km }
    val leader: MemberState? get() = members.maxByOrNull { it.km }
    val ringBearer: MemberState? get() = members.firstOrNull { it.member.id == ringBearerId }
    val nazgulActive: Boolean get() = nazgulPhase == 1 || nazgulPhase == 2
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
    val woundedUntil = mutableMapOf<String, LocalDate>() // výlučně
    val events = mutableListOf(
        Event(
            f.startDate,
            "Společenstvo vyráží z Hobitína. Glum je ${Gollum.HEAD_START_KM.toInt()} km za vámi a Kraj už hledá Černý jezdec.",
            Kind.START,
        )
    )
    val logs = mutableListOf<DayLog>()
    var g = -Gollum.HEAD_START_KM
    var n = Nazgul.START_KM
    var phase = 1

    fun ringBearer(): Member? =
        ms.firstOrNull { it.character == Nazgul.RING_BEARER } ?: ms.maxByOrNull { pos.getValue(it.id) }

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
            val factor = if (woundedUntil[m.id]?.isAfter(d) == true) 0.5 else 1.0
            val after = (before + s * m.strideCm / 100_000.0 * factor).coerceAtMost(total)
            pos[m.id] = after
            Route.points.filter { it.km > before && it.km <= after }.forEach { w ->
                val finish = w.km >= total
                events += Event(key, "${m.name}: ${w.name}. ${w.note}", if (finish) Kind.FINISH else Kind.WAYPOINT)
            }
        }

        // fáze Nazgûla podle polohy Prstenonoše
        ringBearer()?.let { rb ->
            val p = pos.getValue(rb.id)
            if (phase == 1 && p >= Nazgul.FORD_KM) {
                phase = 0
                events += Event(key, "Povodeň u Brodu Bruinenu smetla Černého jezdce! ${rb.name} je v bezpečí… prozatím.", Kind.ENEMY)
            }
            if (phase == 0 && p >= Nazgul.MARSH_KM && p < total) {
                phase = 2
                n = p - Nazgul.WINGED_GAP_KM
                events += Event(key, "Nad Mrtvými bažinami se ozval děsivý skřek. Nazgûl se vrátil – tentokrát na křídlech!", Kind.ENEMY)
            }
            if (phase == 2 && p >= total) {
                phase = 3
                events += Event(key, "Prsten je zničen a Nazgûlové se rozplynuli jako dým.", Kind.ENEMY)
            }
        }

        var gd: GollumDay? = null
        var ndLog: EnemyDay? = null
        if (d.isBefore(today)) {
            // noc: Glum
            gd = f.gollumDays[key]
            if (gd != null) {
                g += Gollum.km(gd.steps)
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
            // noc: Nazgûl
            val nd = f.nazgulDays[key]
            if (nd != null && (phase == 1 || phase == 2)) {
                val moved = nd.km * (if (phase == 2) Nazgul.WINGED_SPEED else 1.0)
                n += moved
                ndLog = EnemyDay(moved, nd.reason)
                val rb = ringBearer()
                if (rb != null) {
                    val p = pos.getValue(rb.id)
                    if (p < total && n >= p && n > 0.0) {
                        woundedUntil[rb.id] = d.plusDays(1L + Nazgul.WOUND_DAYS)
                        n -= Nazgul.KNOCKBACK_KM
                        events += Event(
                            key,
                            "${if (phase == 2) "Okřídlený Nazgûl" else "Černý jezdec"} dostihl Prstenonoše (${rb.name})! " +
                                "Zásah morgulskou čepelí – ${Nazgul.WOUND_DAYS} dny se počítá jen polovina kroků.",
                            Kind.CAUGHT,
                        )
                    }
                }
            }
        }
        logs += DayLog(key, familySteps, gd, ndLog)
        d = d.plusDays(1)
    }

    val todayKey = today.toString()
    val states = ms.map {
        val until = woundedUntil[it.id]
        val left = if (until != null && until.isAfter(today)) ChronoUnit.DAYS.between(today, until).toInt() else 0
        MemberState(it, pos.getValue(it.id), it.stepsOn(todayKey), steps.getValue(it.id), caught.getValue(it.id), left)
    }
    return GameState(states, g, events, logs, n, phase, ringBearer()?.id)
}
