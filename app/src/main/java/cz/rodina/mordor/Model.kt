package cz.rodina.mordor

data class Member(
    val id: String,
    val name: String,
    val character: String,
    val photo: String?,
    val strideCm: Int,
    val health: Map<String, Long>,
    val manual: Map<String, Long>,
    /** den → "heal" (vyléčit zranění) nebo "gollum" (Glum tu noc stojí) */
    val lembasUsed: Map<String, String> = emptyMap(),
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
    val lembasThreshold: Long = 15000L,
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

