package com.eduardo.horarios.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScheduleEntity::class, ActivityEntity::class, CompletionEntity::class, OverrideEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun activityDao(): ActivityDao
    abstract fun completionDao(): CompletionDao
    abstract fun overrideDao(): OverrideDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2: tabla de actividades hechas. No toca los datos existentes. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `completions` (" +
                        "`activityId` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`activityId`, `epochDay`), " +
                        "FOREIGN KEY(`activityId`) REFERENCES `activities`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
            }
        }

        /** v2 → v3: excepciones, días libres y retrasos puntuales. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_overrides` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`scheduleId` INTEGER NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`type` INTEGER NOT NULL, " +
                        "`activityId` INTEGER, " +
                        "`fromMinute` INTEGER NOT NULL, " +
                        "`minutes` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_day_overrides_epochDay` ON `day_overrides` (`epochDay`)")
            }
        }

        /** v3 → v4: actividades de un solo día y «Seguir en Progreso». */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `activities` ADD COLUMN `onDate` INTEGER")
                db.execSQL("ALTER TABLE `activities` ADD COLUMN `tracked` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** Todas las migraciones, en orden (también las usan las pruebas). */
        val MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "horarios.db",
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
