package cz.rodina.mordor

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firestore:
 *  families/{kód}                 – výprava (start, město, Glum)
 *  families/{kód}/members/{id}    – člen (jméno, postava, fotka, kroky)
 */
class Repo {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun ensureAuth() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    private fun familyRef(code: String) = db.collection("families").document(code)
    private fun memberRef(code: String, id: String) = familyRef(code).collection("members").document(id)

    suspend fun familyExists(code: String): Boolean = familyRef(code).get().await().exists()

    suspend fun createFamily(code: String, name: String, city: String, lat: Double?, lon: Double?) {
        familyRef(code).set(
            mapOf(
                "name" to name,
                "startDate" to LocalDate.now().toString(),
                "city" to city,
                "lat" to lat,
                "lon" to lon,
                "gollumBase" to 8000L,
                "gollumDays" to emptyMap<String, Any>(),
            )
        ).await()
    }

    suspend fun updateFamily(code: String, fields: Map<String, Any?>) {
        familyRef(code).set(fields, SetOptions.merge()).await()
    }

    /** Nová cesta od dneška – členové a profily zůstanou, Glum začne znovu. */
    suspend fun restartFamily(code: String) {
        familyRef(code).update(
            mapOf("startDate" to LocalDate.now().toString(), "gollumDays" to emptyMap<String, Any>())
        ).await()
    }

    /** Smaže výpravu včetně všech členů. */
    suspend fun deleteFamily(code: String) {
        val ms = familyRef(code).collection("members").get().await()
        ms.documents.forEach { it.reference.delete().await() }
        familyRef(code).delete().await()
    }

    suspend fun saveMember(code: String, id: String, name: String, character: String, photo: String?, strideCm: Int) {
        memberRef(code, id).set(
            mapOf("name" to name, "character" to character, "photo" to photo, "strideCm" to strideCm.toLong()),
            SetOptions.merge(),
        ).await()
    }

    private suspend fun updatePaths(code: String, id: String?, values: List<Pair<FieldPath, Any>>) {
        if (values.isEmpty()) return
        val ref = if (id == null) familyRef(code) else memberRef(code, id)
        val rest = values.drop(1).flatMap { listOf(it.first, it.second) }.toTypedArray()
        ref.update(values[0].first, values[0].second, *rest).await()
    }

    suspend fun setHealthSteps(code: String, id: String, days: Map<String, Long>) =
        updatePaths(code, id, days.map { FieldPath.of("health", it.key) to (it.value as Any) })

    suspend fun setManual(code: String, id: String, day: String, steps: Long?) =
        updatePaths(code, id, listOf(FieldPath.of("manual", day) to (steps ?: FieldValue.delete())))

    suspend fun setGollumDays(code: String, days: Map<String, GollumDay>) =
        updatePaths(code, null, days.map {
            FieldPath.of("gollumDays", it.key) to (mapOf("steps" to it.value.steps, "reason" to it.value.reason) as Any)
        })

    fun familyFlow(code: String): Flow<Family?> = callbackFlow {
        val reg = familyRef(code).addSnapshotListener { s, e ->
            if (e != null) return@addSnapshotListener
            trySend(s?.takeIf { it.exists() }?.let { parseFamily(code, it) })
        }
        awaitClose { reg.remove() }
    }

    fun membersFlow(code: String): Flow<List<Member>> = callbackFlow {
        val reg = familyRef(code).collection("members").addSnapshotListener { qs, e ->
            if (e != null || qs == null) return@addSnapshotListener
            trySend(qs.documents.map { parseMember(it) }.sortedBy { it.name })
        }
        awaitClose { reg.remove() }
    }

    private fun longMap(v: Any?): Map<String, Long> =
        (v as? Map<*, *>)?.mapNotNull { (k, x) ->
            val n = x as? Number
            if (k is String && n != null) k to n.toLong() else null
        }?.toMap() ?: emptyMap()

    private fun parseFamily(code: String, s: DocumentSnapshot): Family {
        val gd = (s.get("gollumDays") as? Map<*, *>)?.mapNotNull { (k, v) ->
            val m = v as? Map<*, *>
            val st = (m?.get("steps") as? Number)?.toLong()
            if (k is String && st != null) k to GollumDay(st, m?.get("reason") as? String ?: "") else null
        }?.toMap() ?: emptyMap()
        return Family(
            code = code,
            name = s.getString("name") ?: "Společenstvo",
            startDate = s.getString("startDate") ?: LocalDate.now().toString(),
            city = s.getString("city") ?: "",
            lat = s.getDouble("lat"),
            lon = s.getDouble("lon"),
            gollumBase = s.getLong("gollumBase") ?: 8000L,
            gollumDays = gd,
        )
    }

    private fun parseMember(s: DocumentSnapshot) = Member(
        id = s.id,
        name = s.getString("name") ?: "?",
        character = s.getString("character") ?: "",
        photo = s.getString("photo"),
        strideCm = (s.getLong("strideCm") ?: 70L).toInt(),
        health = longMap(s.get("health")),
        manual = longMap(s.get("manual")),
    )
}
