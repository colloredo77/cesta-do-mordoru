package cz.rodina.mordor

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Forecast(val gollum: GollumDay, val nazgul: EnemyDay, val weather: DayWeather)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("mordor", Context.MODE_PRIVATE)
    private val repo = Repo()
    val health = Health(app)

    var familyCode by mutableStateOf(prefs.getString("family", null)); private set
    var memberId by mutableStateOf(prefs.getString("member", null)); private set
    var lastSync by mutableStateOf(prefs.getString("lastSync", null)); private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var update by mutableStateOf<UpdateInfo?>(null); private set
    private var lastUpdateCheck = 0L

    val family = MutableStateFlow<Family?>(null)
    val members = MutableStateFlow<List<Member>>(emptyList())
    private val today = MutableStateFlow(LocalDate.now())

    val game: StateFlow<GameState?> = combine(family, members, today) { f, ms, t ->
        f?.let { simulate(it, ms, t) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var listenJob: Job? = null
    private var enemiesBusy = false
    var forecast by mutableStateOf<Forecast?>(null); private set
    private var forecastAt = 0L
    var reportDay by mutableStateOf(prefs.getString("report", null)); private set
    private var firstSyncDone = false

    init {
        viewModelScope.launch {
            try {
                repo.ensureAuth()
                familyCode?.let { listen(it) }
            } catch (e: Exception) {
                message = "Nepodařilo se připojit k Firebase: ${e.message}"
            }
            ready = true
        }
    }

    private fun listen(code: String) {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            var seen = false
            launch {
                repo.familyFlow(code).collect { f ->
                    if (f == null && seen) {
                        leave()
                        message = "Výprava byla smazána. Můžete založit novou."
                        return@collect
                    }
                    family.value = f
                    if (f != null) {
                        seen = true
                        launch { ensureEnemies(f) }
                        launch { refreshForecast(f) }
                        if (!firstSyncDone) { firstSyncDone = true; launch { syncHealthNow(silent = true) } }
                    }
                }
            }
            launch { repo.membersFlow(code).collect { members.value = it } }
        }
    }

    /** Dopočítá noční pohyb Gluma a Nazgûla za uplynulé dny, které ještě nemají (podle počasí). */
    private suspend fun ensureEnemies(f: Family) {
        if (enemiesBusy) return
        val now = LocalDate.now()
        val yesterday = now.minusDays(1)
        val start = runCatching { LocalDate.parse(f.startDate) }.getOrNull() ?: return
        val days = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(yesterday) }
            .map { it.toString() }
            .toList()
        val missingG = days.filter { it !in f.gollumDays }
        val missingN = days.filter { it !in f.nazgulDays }
        if (missingG.isEmpty() && missingN.isEmpty()) return
        enemiesBusy = true
        try {
            val hasPlace = f.lat != null && f.lon != null
            val weather: Map<String, DayWeather> = if (hasPlace) {
                runCatching { Weather.pastDays(f.lat!!, f.lon!!, 92) }.getOrNull() ?: return // offline – zkusíme příště
            } else emptyMap()
            val cutoff = now.minusDays(90).toString()
            fun skip(day: String) = weather[day] == null && hasPlace && day >= cutoff
            val g = missingG.filterNot { skip(it) }.associateWith { Gollum.stepsFor(it, f.gollumBase, weather[it]) }
            val n = missingN.filterNot { skip(it) }.associateWith { Nazgul.kmFor(it, f.nazgulBase, weather[it]) }
            repo.setEnemyDays(f.code, g, n)
        } catch (e: Exception) {
            message = "Nepřátelé se zasekli: ${e.message}"
        } finally {
            enemiesBusy = false
        }
    }

    /** Předpověď, jak rychle se nepřátelé pohnou dnešní noc. */
    private suspend fun refreshForecast(f: Family) {
        if (System.currentTimeMillis() - forecastAt < 3 * 60 * 60 * 1000L && forecast != null) return
        val lat = f.lat ?: return
        val lon = f.lon ?: return
        val w = runCatching { Weather.pastDays(lat, lon, 1) }.getOrNull() ?: return
        val key = LocalDate.now().toString()
        val today = w[key] ?: return
        forecast = Forecast(Gollum.stepsFor(key, f.gollumBase, today), Nazgul.kmFor(key, f.nazgulBase, today), today)
        forecastAt = System.currentTimeMillis()
    }

    fun dismissReport() {
        val t = LocalDate.now().toString()
        prefs.edit().putString("report", t).apply()
        reportDay = t
    }

    fun onResume() {
        today.value = LocalDate.now()
        if (System.currentTimeMillis() - lastUpdateCheck > 60 * 60 * 1000L) checkUpdate(manual = false)
        if (familyCode != null && memberId != null) viewModelScope.launch { syncHealthNow(silent = true) }
        family.value?.let { f ->
            viewModelScope.launch { ensureEnemies(f) }
            viewModelScope.launch { refreshForecast(f) }
        }
    }

    fun createFamily(name: String, city: String) = viewModelScope.launch {
        busy = true
        try {
            repo.ensureAuth()
            val place = if (city.isNotBlank()) runCatching { Weather.geocode(city) }.getOrNull() else null
            if (city.isNotBlank() && place == null) {
                message = "Město se nepodařilo najít – Glum půjde bez počasí. Změníte v Nastavení."
            }
            val code = newCode()
            repo.createFamily(code, name.ifBlank { "Společenstvo" }, place?.name ?: city, place?.lat, place?.lon)
            setFamily(code)
        } catch (e: Exception) {
            message = "Chyba: ${e.message}"
        } finally {
            busy = false
        }
    }

    fun joinFamily(code: String) = viewModelScope.launch {
        busy = true
        try {
            repo.ensureAuth()
            val c = code.trim().uppercase()
            if (repo.familyExists(c)) setFamily(c) else message = "Výprava s kódem $c neexistuje."
        } catch (e: Exception) {
            message = "Chyba: ${e.message}"
        } finally {
            busy = false
        }
    }

    private fun setFamily(code: String) {
        prefs.edit().putString("family", code).apply()
        familyCode = code
        firstSyncDone = false
        listen(code)
    }

    fun saveProfile(name: String, character: String, photo: String?, strideCm: Int) = viewModelScope.launch {
        val code = familyCode ?: return@launch
        busy = true
        try {
            val id = memberId ?: UUID.randomUUID().toString()
            repo.saveMember(code, id, name, character, photo, strideCm)
            claimMember(id)
            message = "Profil uložen."
            syncHealthNow(silent = true)
        } catch (e: Exception) {
            message = "Uložení selhalo: ${e.message}"
        } finally {
            busy = false
        }
    }

    fun claimMember(id: String) {
        prefs.edit().putString("member", id).apply()
        memberId = id
    }

    fun syncHealth() = viewModelScope.launch {
        busy = true
        syncHealthNow(silent = false)
        busy = false
    }

    private suspend fun syncHealthNow(silent: Boolean) {
        val code = familyCode ?: return
        val id = memberId ?: return
        if (members.value.none { it.id == id } && silent) return
        if (!health.available()) {
            if (!silent) message = "Health Connect není v telefonu dostupný – zadávejte kroky ručně."
            return
        }
        try {
            if (!health.hasPermission()) {
                if (!silent) message = "Chybí oprávnění číst kroky."
                return
            }
            val now = LocalDate.now()
            val start = family.value?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: now
            val monthAgo = now.minusDays(29)
            val from = if (start.isAfter(monthAgo)) start else monthAgo
            val steps = health.dailySteps(from, now)
            repo.setHealthSteps(code, id, steps)
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d. M. HH:mm"))
            lastSync = stamp
            prefs.edit().putString("lastSync", stamp).apply()
            if (!silent) message = "Načteno z hodinek: dnes ${(steps[now.toString()] ?: 0L).fmt()} kroků."
        } catch (e: Exception) {
            if (!silent) message = "Načtení kroků selhalo: ${e.message}"
        }
    }

    fun setManual(member: String, day: String, steps: Long?) = viewModelScope.launch {
        val code = familyCode ?: return@launch
        try {
            repo.setManual(code, member, day, steps)
        } catch (e: Exception) {
            message = "Uložení selhalo: ${e.message}"
        }
    }

    fun updateFamily(name: String, city: String, gollumBase: Long, nazgulBase: Double) = viewModelScope.launch {
        val f = family.value ?: return@launch
        busy = true
        try {
            val fields = mutableMapOf<String, Any?>("name" to name.ifBlank { f.name }, "gollumBase" to gollumBase, "nazgulBase" to nazgulBase)
            forecastAt = 0L
            if (city.isNotBlank() && city != f.city) {
                val place = runCatching { Weather.geocode(city) }.getOrNull()
                if (place == null) {
                    message = "Město „$city“ jsem nenašel."
                } else {
                    fields["city"] = place.name
                    fields["lat"] = place.lat
                    fields["lon"] = place.lon
                }
            }
            repo.updateFamily(f.code, fields)
            if (message == null) message = "Uloženo. Nepřátelé se řídí novým nastavením od dnešní noci."
        } catch (e: Exception) {
            message = "Uložení selhalo: ${e.message}"
        } finally {
            busy = false
        }
    }

    fun checkUpdate(manual: Boolean) = viewModelScope.launch {
        lastUpdateCheck = System.currentTimeMillis()
        try {
            update = Updater.check()
            if (manual) message = update?.let { "Je k dispozici nová verze ${it.version}." } ?: "Máte nejnovější verzi (${Updater.current})."
        } catch (e: PrivateRepoException) {
            if (manual) message = "Aktualizace nejde zkontrolovat – repozitář na GitHubu je soukromý."
        } catch (e: Exception) {
            if (manual) message = "Kontrola aktualizace selhala: ${e.message}"
        }
    }

    fun restartJourney() = viewModelScope.launch {
        val code = familyCode ?: return@launch
        busy = true
        try {
            repo.restartFamily(code)
            message = "Nová cesta začíná! Všichni stojí v Hobitíně, Glum je opět ${Gollum.HEAD_START_KM.toInt()} km za vámi."
        } catch (e: Exception) {
            message = "Nepodařilo se: ${e.message}"
        } finally {
            busy = false
        }
    }

    fun deleteFamily() = viewModelScope.launch {
        val code = familyCode ?: return@launch
        busy = true
        try {
            listenJob?.cancel()
            repo.deleteFamily(code)
            leave()
            message = "Výprava smazána."
        } catch (e: Exception) {
            message = "Smazání selhalo: ${e.message}"
            listen(code)
        } finally {
            busy = false
        }
    }

    fun leave() {
        listenJob?.cancel()
        prefs.edit().remove("family").remove("member").apply()
        familyCode = null
        memberId = null
        family.value = null
        members.value = emptyList()
    }

    private fun newCode(): String {
        val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { abc.random() }.joinToString("")
    }
}
