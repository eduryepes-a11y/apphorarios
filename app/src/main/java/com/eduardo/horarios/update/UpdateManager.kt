package com.eduardo.horarios.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
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
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Error(val messageRes: Int, val detail: String?, val info: UpdateInfo?) : UpdateState
}

/**
 * Actualizaciones desde GitHub Releases:
 * 1. Mira la última Release del repositorio.
 * 2. Si su versión (tag "v1.2.0") es mayor que la instalada y trae un .apk, avisa.
 * 3. Descarga el APK y lo instala con PackageInstaller (en Android 12+ puede ir sin confirmación).
 */
object UpdateManager {
    const val REPO = "eduryepes-a11y/apphorarios"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val ACTION_INSTALL_STATUS = "com.eduardo.horarios.INSTALL_STATUS"

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
        val busy = _state.value is UpdateState.Downloading || _state.value is UpdateState.Installing
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

    suspend fun downloadAndInstall(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
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
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
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

            _state.value = UpdateState.Installing(info)
            install(context, file)
        } catch (e: Exception) {
            _state.value = UpdateState.Error(R.string.update_error_download, null, info)
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Si Android lo permite, se actualiza sin pedir confirmación
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("horarios.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(
                context,
                7_777,
                Intent(context, UpdateReceiver::class.java).setAction(ACTION_INSTALL_STATUS),
                flags,
            )
            session.commit(pi.intentSender)
        }
    }

    internal fun onInstallFinished(success: Boolean, message: String?) {
        val info = when (val s = _state.value) {
            is UpdateState.Installing -> s.info
            is UpdateState.Available -> s.info
            else -> null
        }
        _state.value = if (success) {
            UpdateState.UpToDate
        } else if (info != null) {
            UpdateState.Error(R.string.update_error_install, message, info)
        } else {
            UpdateState.Idle
        }
    }

    internal fun onInstallCancelled() {
        val s = _state.value
        if (s is UpdateState.Installing) _state.value = UpdateState.Available(s.info)
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
