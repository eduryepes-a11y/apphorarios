package com.eduardo.horarios

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eduardo.horarios.data.AppDatabase
import com.eduardo.horarios.data.OverrideEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba que quien actualiza desde una versión antigua conserva sus horarios.
 * Crea la base de datos tal y como la dejaban las versiones 1.0 (v1) y 1.1 (v2)
 * y la abre con la versión actual: Room valida que el esquema final es correcto.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    private fun createOld(version: Int) {
        context.deleteDatabase(name)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, " +
                "`isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`scheduleId` INTEGER NOT NULL, `title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "`daysMask` INTEGER NOT NULL, `startMinute` INTEGER NOT NULL, `endMinute` INTEGER NOT NULL, " +
                "`colorIndex` INTEGER NOT NULL, `reminderMinutes` INTEGER NOT NULL, " +
                "FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_scheduleId` ON `activities` (`scheduleId`)")
        if (version >= 2) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `completions` (`activityId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, " +
                    "`completedAt` INTEGER NOT NULL, PRIMARY KEY(`activityId`, `epochDay`), " +
                    "FOREIGN KEY(`activityId`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }
        db.execSQL("INSERT INTO schedules (id, name, emoji, colorIndex, isActive, createdAt) VALUES (1, 'Semana normal', '📅', 0, 1, 0)")
        db.execSQL(
            "INSERT INTO activities (id, scheduleId, title, emoji, notes, daysMask, startMinute, endMinute, colorIndex, reminderMinutes) " +
                "VALUES (1, 1, 'Trabajo', '💼', '', 31, 540, 840, 1, 10)"
        )
        if (version >= 2) db.execSQL("INSERT INTO completions (activityId, epochDay, completedAt) VALUES (1, 20000, 0)")
        db.version = version
        db.close()
    }

    private fun openCurrent(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    private fun count(db: AppDatabase, table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun checkUpgraded(fromVersion: Int) {
        createOld(fromVersion)
        val db = openCurrent()
        try {
            assertEquals(1, count(db, "schedules"))
            assertEquals(1, count(db, "activities"))
            assertEquals(if (fromVersion >= 2) 1 else 0, count(db, "completions"))
            // La tabla nueva funciona
            runBlocking {
                db.overrideDao().insert(OverrideEntity(scheduleId = 1, epochDay = 20000, type = OverrideEntity.TYPE_SKIP, activityId = 1))
            }
            assertEquals(1, count(db, "day_overrides"))
        } finally {
            db.close()
        }
    }

    @Test
    fun desdeVersion1() = checkUpgraded(1)

    @Test
    fun desdeVersion2() = checkUpgraded(2)
}
