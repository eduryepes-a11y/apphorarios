package com.eduardo.horarios.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.eduardo.horarios.R
import com.eduardo.horarios.data.BackupFormat
import com.eduardo.horarios.data.HorariosFile
import com.eduardo.horarios.data.HorariosRepository
import com.eduardo.horarios.data.InvalidFileException
import com.eduardo.horarios.data.ScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Archivo pendiente de importar (abierto desde WhatsApp, Archivos… o elegido en la app). */
object ImportController {
    val pending = MutableStateFlow<Uri?>(null)
}

object BackupIO {
    private const val MAX_BYTES = 2 * 1024 * 1024

    fun backupFileName(): String = "horarios-backup-${LocalDate.now()}.json"

    /** Guarda una copia completa en el archivo que ha elegido el usuario. */
    suspend fun writeBackup(context: Context, repo: HorariosRepository, uri: Uri) = withContext(Dispatchers.IO) {
        val json = BackupFormat.toJson(HorariosFile.KIND_BACKUP, repo.exportAll())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("No output stream")
    }

    suspend fun readFile(context: Context, uri: Uri): HorariosFile = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val data = input.readBytes()
            if (data.size > MAX_BYTES) throw InvalidFileException()
            data
        } ?: throw InvalidFileException()
        BackupFormat.parse(bytes.toString(Charsets.UTF_8))
    }

    /** Abre el menú de compartir de Android con el horario como archivo .json. */
    suspend fun shareSchedule(context: Context, repo: HorariosRepository, schedule: ScheduleEntity) {
        val uri = withContext(Dispatchers.IO) {
            val export = repo.exportSchedule(schedule, withDone = false)
            val json = BackupFormat.toJson(HorariosFile.KIND_SCHEDULE, listOf(export))
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val safeName = schedule.name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.trim().ifEmpty { "horario" }
            val file = File(dir, "$safeName.json")
            file.writeText(json, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, "${schedule.emoji} ${schedule.name}"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_title))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
