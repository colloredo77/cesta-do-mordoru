package cz.rodina.mordor

import kotlin.math.min

data class Member(
    val id: String,
    val name: String,
    val character: String,
    val photo: String?,
    val strideCm: Int,
    val health: Map<String, Long>,
    val manual: Map<String, Long>,
) {
    /** Ruční zadání má přednost před daty z hodinek. */
    fun stepsOn(day: String): Long = manual[day] ?: health[day] ?: 0L
}

data class GollumDay(val steps: Long, val reason: String)
data class EnemyDay(val km: Double, val reason: String)

data class Family(
    val code: String,
    val name: String,
    val startDate: String,
    val city: String,
    val lat: Double?,
    val lon: Double?,
    val gollumBase: Long,
    val gollumDays: Map<String, GollumDay>,
    val nazgulBase: Double = 6.0,
    val nazgulDays: Map<String, EnemyDay> = emptyMap(),
)

object Characters {
    val list = listOf(
        "Frodo Pytlík",
        "Samvěd Křepelka",
        "Smělmír Brandorád",
        "Peregrin Bral",
        "Gandalf",
        "Aragorn",
        "Legolas",
        "Gimli",
        "Boromir",
        "Bilbo Pytlík",
    )
}

/** Zastávka na cestě. x, y = poloha na vlastní stylizované mapě (0..1). km = vzdálenost od startu. */
data class Waypoint(val name: String, val km: Double, val x: Float, val y: Float, val note: String)

object Route {
    val points = listOf(
        Waypoint("Dno pytle", 0.0, .10f, .30f, "Výprava vyráží z Hobitína. Dobrodružství začíná za prahem."),
        Waypoint("Rádovsko", 64.0, .165f, .315f, "Přes Brandyvínu na pramici. Poslední klidný nocleh."),
        Waypoint("Starý hvozd", 85.0, .20f, .33f, "Stromy tu šeptají a pěšiny se samy stáčejí."),
        Waypoint("Mohylové vrchy", 130.0, .23f, .30f, "Mlha nad mohylami. Raději nezastavovat."),
        Waypoint("Hůrka", 233.0, .27f, .29f, "Hostinec U Skákavého poníka – teplé jídlo a cizinec v koutě."),
        Waypoint("Větrov", 330.0, .33f, .275f, "Rozvaliny strážní věže na kopci. Noc je tu nebezpečná."),
        Waypoint("Poslední most", 420.0, .39f, .28f, "Odtud už žádné pohodlné cesty."),
        Waypoint("Brod Bruinenu", 640.0, .455f, .27f, "Řeka chrání Roklinku – rychle na druhý břeh!"),
        Waypoint("Roklinka", 735.0, .48f, .255f, "Útočiště elfů. Odpočinek, hostina a rada."),
        Waypoint("Cesmínov", 940.0, .465f, .375f, "Opuštěný kraj pod horami. Nebe hlídají ptáci."),
        Waypoint("Morie", 1150.0, .505f, .43f, "Dlouhá tma pod horami. Tiše a rychle."),
        Waypoint("Lothlórien", 1240.0, .565f, .47f, "Zlatý les, kde čas plyne jinak."),
        Waypoint("Amon Hen", 1866.0, .61f, .645f, "Po řece až k Rauros. Tady se výprava rozdělí."),
        Waypoint("Emyn Muil", 1950.0, .66f, .625f, "Bludiště ostrých skal. Někdo vás sleduje…"),
        Waypoint("Mrtvé bažiny", 2120.0, .705f, .605f, "Nedívejte se na světýlka ve vodě."),
        Waypoint("Morannon", 2250.0, .755f, .59f, "Černá brána je zavřená. Musí existovat jiná cesta."),
        Waypoint("Henneth Annûn", 2440.0, .70f, .70f, "Skrytý úkryt za vodopádem v Ithilienu."),
        Waypoint("Křižovatka", 2520.0, .715f, .755f, "Kamenný král s korunou z květů."),
        Waypoint("Minas Morgul", 2560.0, .74f, .775f, "Bledé město. Nedívat se nahoru."),
        Waypoint("Kirith Ungol", 2610.0, .765f, .745f, "Schody, tunel a něco ve tmě…"),
        Waypoint("Hora osudu", 2863.0, .86f, .70f, "Cíl! Prsten skončil v ohni. Středozem je zachráněna."),
    )

    val totalKm: Double get() = points.last().km

    fun positionAt(km: Double): Pair<Float, Float> {
        val p = points
        if (km <= 0.0) {
            // nepřátelé před startem čekají jihozápadně od Hobitína
            val f = (min(-km, 80.0) / 80.0).toFloat()
            return (p[0].x - 0.06f * f) to (p[0].y + 0.16f * f)
        }
        for (i in 1 until p.size) {
            if (km <= p[i].km) {
                val a = p[i - 1]
                val b = p[i]
                val t = ((km - a.km) / (b.km - a.km)).toFloat()
                return (a.x + (b.x - a.x) * t) to (a.y + (b.y - a.y) * t)
            }
        }
        return p.last().x to p.last().y
    }

    fun lastReached(km: Double): Waypoint = points.last { it.km <= km.coerceAtLeast(0.0) }
    fun next(km: Double): Waypoint? = points.firstOrNull { it.km > km }
}
