package com.eduardo.horarios.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.eduardo.horarios.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo) : UpdateState
    data class Error(val messageRes: Int, val detail: String?, val info: UpdateInfo?) : UpdateState
}

/**
 * Actualizaciones desde GitHub Releases:
 * 1. Mira la última Release del repositorio.
 * 2. Si su versión (tag "v1.2.0") es mayor que la instalada y trae un .apk, avisa.
 * 3. Descarga el APK y abre el instalador de Android para confirmar la actualización.
 */
object UpdateManager {
    const val REPO = "eduryepes-a11y/apphorarios"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var lastCheck = 0L

    fun currentVersion(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }

    /**
     * Comprueba si hay versión nueva. En modo [silent] no muestra "comprobando" ni errores
     * y no repite la consulta si se hizo hace menos de 30 minutos.
     */
    suspend fun check(context: Context, silent: Boolean) = withContext(Dispatchers.IO) {
        val busy = _state.value is UpdateState.Downloading
        if (busy) return@withContext
        if (silent && System.currentTimeMillis() - lastCheck < 30 * 60_000L) return@withContext
        lastCheck = System.currentTimeMillis()
        if (!silent) _state.value = UpdateState.Checking

        try {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Horarios-App")
            }
            val code = conn.responseCode
            if (code == 404) {
                _state.value = UpdateState.UpToDate
                return@withContext
            }
            if (code != 200) throw IllegalStateException("HTTP $code")

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val remote = json.optString("tag_name").removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var size = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        size = a.optLong("size")
                        break
                    }
                }
            }

            _state.value = if (apkUrl != null && isNewer(remote, currentVersion(context))) {
                UpdateState.Available(
                    UpdateInfo(
                        versionName = remote,
                        notes = json.optString("body").trim(),
                        apkUrl = apkUrl,
                        sizeBytes = size,
                    )
                )
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            _state.value = if (silent) UpdateState.Idle
            else UpdateState.Error(R.string.update_error_check, null, null)
        }
    }

    /** true si Android deja a esta app instalar APKs (permiso «instalar apps desconocidas»). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    private fun apkFile(context: Context): File = File(File(context.cacheDir, "updates").apply { mkdirs() }, "Horarios.apk")

    /** Descarga el APK y abre el instalador de Android. */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        val file = apkFile(context)
        try {
            _state.value = UpdateState.Downloading(info, 0f)
            var conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Horarios-App")
            // GitHub redirige a otro dominio: seguimos las redirecciones a mano por si acaso
            var redirects = 0
            while (conn.responseCode in 300..399 && redirects < 5) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                conn = URL(location).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Horarios-App")
                redirects++
            }
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")

            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var done = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var lastEmit = 0L
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0 && done - lastEmit > 128 * 1024) {
                            lastEmit = done
                            _state.value = UpdateState.Downloading(info, done.toFloat() / total)
                        }
                    }
                }
            }
            if (info.sizeBytes > 0 && file.length() != info.sizeBytes) throw IllegalStateException("Incomplete")

            _state.value = UpdateState.ReadyToInstall(info)
            launchInstaller(context)
        } catch (e: Exception) {
            file.delete()
            _state.value = UpdateState.Error(R.string.update_error_download, null, info)
        }
    }

    /** Vuelve a abrir el instalador con el APK ya descargado (o lo descarga si no está). */
    suspend fun install(context: Context, info: UpdateInfo) {
        val file = apkFile(context)
        if (info.sizeBytes > 0 && file.length() == info.sizeBytes) {
            _state.value = UpdateState.ReadyToInstall(info)
            withContext(Dispatchers.Main) { launchInstaller(context) }
        } else {
            downloadAndInstall(context, info)
        }
    }

    /** Abre la pantalla de Android «¿Quieres actualizar esta app?». */
    private fun launchInstaller(context: Context) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apkFile(context))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    /** Compara "1.2.10" con "1.2.9" número a número. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.split('.', '-', '+').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
