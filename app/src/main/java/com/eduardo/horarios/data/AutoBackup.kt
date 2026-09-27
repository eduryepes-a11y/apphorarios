package com.eduardo.horarios.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Nombres y limpieza de las copias automáticas. Sin Android: se prueba con JUnit. */
object AutoBackupFiles {
    const val KEEP = 7
    private val NAME = Regex("""auto-(\d{4}-\d{2}-\d{2})\.json""")

    fun nameFor(date: LocalDate): String = "auto-$date.json"

    /** Fecha de una copia a partir de su nombre (null si no es una copia automática). */
    fun dateOf(name: String): LocalDate? =
        NAME.matchEntire(name)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Copias que sobran: se guardan las [keep] más recientes. */
    fun toPrune(names: List<String>, keep: Int = KEEP): List<String> =
        names.mapNotNull { n -> dateOf(n)?.let { n to it } }
            .sortedByDescending { it.second }
            .drop(keep)
            .map { it.first }
}

/**
 * Copia de seguridad automática: una al día (al abrir la app o a las 00:01), dentro de la app.
 * Se guardan las 7 últimas. Opcionalmente también en una carpeta elegida por el usuario
 * (Drive, Descargas…), siempre en el mismo archivo «Horarios-copia.json».
 */
object AutoBackup {
    private const val PREFS = "auto_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_FOLDER = "folder"
    private const val KEY_FOLDER_ERROR = "folder_error"
    const val FOLDER_FILE = "Horarios-copia.json"
    private val mutex = Mutex()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun dir(context: Context): File = File(context.filesDir, "backups")

    /** Carpeta extra elegida por el usuario (árbol de documentos), o null. */
    fun folder(context: Context): Uri? = prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse)

    /** true si la última vez no se pudo escribir en la carpeta elegida. */
    fun folderError(context: Context): Boolean = prefs(context).getBoolean(KEY_FOLDER_ERROR, false)

    fun setFolder(context: Context, uri: Uri?) {
        val cr = context.contentResolver
        folder(context)?.let { old ->
            runCatching { cr.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        if (uri != null) {
            runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        prefs(context).edit().putString(KEY_FOLDER, uri?.toString()).putBoolean(KEY_FOLDER_ERROR, false).apply()
    }

    /** Copias automáticas guardadas, de la más reciente a la más antigua. */
    fun list(context: Context): List<File> =
        (dir(context).listFiles() ?: emptyArray())
            .filter { AutoBackupFiles.dateOf(it.name) != null }
            .sortedByDescending { AutoBackupFiles.dateOf(it.name) }

    /**
     * Hace la copia del día si toca. Con [force] la rehace aunque ya exista.
     * No hace nada si está desactivada o si aún no hay ningún horario.
     */
    suspend fun runIfDue(context: Context, repo: HorariosRepository, today: LocalDate = LocalDate.now(), force: Boolean = false): File? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!isEnabled(context)) return@withLock null
                val dir = dir(context).apply { mkdirs() }
                val file = File(dir, AutoBackupFiles.nameFor(today))
                if (file.exists() && !force) return@withLock null
                val schedules = repo.exportAll()
                if (schedules.isEmpty()) return@withLock null
                val json = BackupFormat.toJson(HorariosFile.KIND_BACKUP, schedules)
                val tmp = File(dir, file.name + ".tmp")
                tmp.writeText(json, Charsets.UTF_8)
                if (!tmp.renameTo(file)) {
                    file.writeText(json, Charsets.UTF_8)
                    tmp.delete()
                }
                AutoBackupFiles.toPrune(dir.list()?.toList() ?: emptyList()).forEach { File(dir, it).delete() }
                folder(context)?.let { tree ->
                    val ok = runCatching { writeToFolder(context, tree, json) }.getOrDefault(false)
                    prefs(context).edit().putBoolean(KEY_FOLDER_ERROR, !ok).apply()
                }
                file
            }
        }

    /** Escribe (o sobrescribe) «Horarios-copia.json» en la carpeta elegida. */
    private fun writeToFolder(context: Context, tree: Uri, json: String): Boolean {
        val cr = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        var existing: Uri? = null
        cr.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == FOLDER_FILE) {
                    existing = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                    break
                }
            }
        }
        val target = existing ?: DocumentsContract.createDocument(cr, parent, "application/json", FOLDER_FILE) ?: return false
        cr.openOutputStream(target, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: return false
        return true
    }

    /** Lee una copia automática. */
    fun read(file: File): HorariosFile = BackupFormat.parse(file.readText(Charsets.UTF_8))
}
