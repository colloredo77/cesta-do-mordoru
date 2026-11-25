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

enum class Kind { WAYPOINT, CAUGHT, FINISH, START, ENEMY, LEMBAS }

data class Event(val day: String, val text: String, val kind: Kind, val waypoint: String? = null, val memberId: String? = null)

data class Badge(val id: String, val icon: String, val name: String, val desc: String)

object Badges {
    val list = listOf(
        Badge("d10k", "👣", "Desetitisícovka", "Ujít 10 000 kroků za jeden den"),
        Badge("d20k", "⚡", "Dvacítka", "Ujít 20 000 kroků za jeden den"),
        Badge("d30k", "🏃", "Třicítka", "Ujít 30 000 kroků za jeden den"),
        Badge("s7", "📅", "Vytrvalec", "7 dní v řadě aspoň 10 000 kroků"),
        Badge("s14", "🔥", "Neúnavný poutník", "14 dní v řadě aspoň 10 000 kroků"),
        Badge("k100", "💯", "Stovka", "Ujít celkem 100 km"),
        Badge("k500", "🗺️", "Pětistovka", "Ujít celkem 500 km"),
        Badge("k1000", "🏔️", "Tisícovka", "Ujít celkem 1 000 km"),
        Badge("hurka", "🍺", "U Skákavého poníka", "Dojít do Hůrky"),
        Badge("roklinka", "🏞️", "Host v Roklince", "Dojít do Roklinky"),
        Badge("morie", "⛏️", "Prošel Morií", "Dojít do Lothlórienu"),
        Badge("osud", "💍", "Nositel osudu", "Dojít k Hoře osudu"),
        Badge("vlasek", "😅", "O vlásek", "Glum byl blíž než 2 km, ale nedohonil"),
        Badge("korist", "🐟", "Glumova kořist", "Nechat se dostihnout Glumem"),
        Badge("pekar", "🍞", "Elfí pekař", "Získat 5 lembasů"),
        Badge("lecitel", "🌿", "Léčitel", "Vyléčit zranění lembasem"),
        Badge("krmic", "🤢", "Krmič Gluma", "Nabídnout Glumovi lembas"),
    )
    fun byId(id: String) = list.firstOrNull { it.id == id }
    val waypointBadges = mapOf("Hůrka" to "hurka", "Roklinka" to "roklinka", "Lothlórien" to "morie", "Hora osudu" to "osud")
}
data class DayLog(val day: String, val familySteps: Long, val gollum: GollumDay?, val nazgul: EnemyDay?)
data class MemberState(
    val member: Member,
    val km: Double,
    val todaySteps: Long,
    val totalSteps: Long,
    val caught: Int,
    val woundedDaysLeft: Int = 0,
    val lembas: Int = 0,
    val lembasEarned: Int = 0,
    /** id odznaku → den získání */
    val badges: Map<String, String> = emptyMap(),
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
    val walked = ms.associate { it.id to 0.0 }.toMutableMap()
    val streak = ms.associate { it.id to 0 }.toMutableMap()
    val earned = ms.associate { it.id to 0 }.toMutableMap()
    val badges = ms.associate { it.id to LinkedHashMap<String, String>() }
    fun award(id: String, badge: String, day: String) { badges[id]?.let { if (badge !in it) it[badge] = day } }
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
            // lembas na vyléčení platí už pro tento den
            if (m.lembasUsed[key] == "heal") {
                woundedUntil.remove(m.id)
                award(m.id, "lecitel", key)
                events += Event(key, "${m.name}: lembas zahojil ránu od morgulské čepele.", Kind.LEMBAS, memberId = m.id)
            }
            val s = m.stepsOn(key)
            familySteps += s
            steps[m.id] = steps.getValue(m.id) + s
            if (s >= 10_000) award(m.id, "d10k", key)
            if (s >= 20_000) award(m.id, "d20k", key)
            if (s >= 30_000) award(m.id, "d30k", key)
            if (s >= 10_000) streak[m.id] = streak.getValue(m.id) + 1 else if (d.isBefore(today)) streak[m.id] = 0
            if (streak.getValue(m.id) >= 7) award(m.id, "s7", key)
            if (streak.getValue(m.id) >= 14) award(m.id, "s14", key)
            if (s >= f.lembasThreshold) {
                earned[m.id] = earned.getValue(m.id) + 1
                if (earned.getValue(m.id) >= 5) award(m.id, "pekar", key)
            }
            val before = pos.getValue(m.id)
            if (before >= total) continue
            val factor = if (woundedUntil[m.id]?.isAfter(d) == true) 0.5 else 1.0
            val after = (before + s * m.strideCm / 100_000.0 * factor).coerceAtMost(total)
            pos[m.id] = after
            walked[m.id] = walked.getValue(m.id) + (after - before)
            val w0 = walked.getValue(m.id)
            if (w0 >= 100) award(m.id, "k100", key)
            if (w0 >= 500) award(m.id, "k500", key)
            if (w0 >= 1000) award(m.id, "k1000", key)
            Route.points.filter { it.km > before && it.km <= after }.forEach { w ->
                val finish = w.km >= total
                events += Event(key, "${m.name}: ${w.name}. ${w.note}", if (finish) Kind.FINISH else Kind.WAYPOINT, w.name, m.id)
                Badges.waypointBadges[w.name]?.let { award(m.id, it, key) }
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
            val feeder = ms.firstOrNull { it.lembasUsed[key] == "gollum" }
            if (gd != null && feeder != null) {
                award(feeder.id, "krmic", key)
                events += Event(key, "Lembas pro Gluma (od ${feeder.name}): Glum se elfího chleba štítí, celou noc prskal v úkrytu a ani se nehnul.", Kind.LEMBAS, memberId = feeder.id)
                gd = GollumDay(0, "nabídnut lembas – celou noc prskal v úkrytu")
            }
            if (gd != null) {
                g += Gollum.km(gd.steps)
                for (m in ms.sortedBy { pos.getValue(it.id) }) {
                    val p = pos.getValue(m.id)
                    if (p < total && g >= p && g > 0.0) {
                        pos[m.id] = max(0.0, p - Gollum.CATCH_PENALTY_KM)
                        caught[m.id] = caught.getValue(m.id) + 1
                        award(m.id, "korist", key)
                        g -= Gollum.ESCAPE_KM
                        events += Event(
                            key,
                            "Glum dostihl: ${m.name}! Návrat o ${Gollum.CATCH_PENALTY_KM.toInt()} km. Glum se se syčením stáhl do stínů.",
                            Kind.CAUGHT,
                        )
                    }
                }
            }
            for (m in ms) {
                val p = pos.getValue(m.id)
                if (p < total && g > 0.0 && p > g && p - g < 2.0) award(m.id, "vlasek", key)
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
        val used = it.lembasUsed.keys.count { k -> k >= f.startDate && k <= todayKey }
        MemberState(
            it, pos.getValue(it.id), it.stepsOn(todayKey), steps.getValue(it.id), caught.getValue(it.id), left,
            lembas = (earned.getValue(it.id) - used).coerceAtLeast(0),
            lembasEarned = earned.getValue(it.id),
            badges = badges.getValue(it.id),
        )
    }
    return GameState(states, g, events, logs, n, phase, ringBearer()?.id)
}
