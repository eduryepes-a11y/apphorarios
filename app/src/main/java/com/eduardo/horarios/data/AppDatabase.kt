package com.eduardo.horarios.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ScheduleEntity::class, ActivityEntity::class, CompletionEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun activityDao(): ActivityDao
    abstract fun completionDao(): CompletionDao

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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "horarios.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
