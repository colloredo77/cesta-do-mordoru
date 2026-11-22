package cz.rodina.mordor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: Int, val url: String)

class PrivateRepoException : Exception("Repozitář je soukromý")

/** Kontrola nové verze přes GitHub Releases (repozitář musí být veřejný). */
object Updater {
    val current: Int get() = BuildConfig.VERSION_CODE

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return@withContext null
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode == 404) throw PrivateRepoException()
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val v = j.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return@withContext null
            val assets = j.optJSONArray("assets")
            val url = if (assets != null && assets.length() > 0) {
                assets.getJSONObject(0).getString("browser_download_url")
            } else {
                j.getString("html_url")
            }
            if (v > current) UpdateInfo(v, url) else null
        } finally {
            c.disconnect()
        }
    }
}
