package com.eduardo.horarios.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ScheduleEntity?>

    @Query("SELECT * FROM schedules WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): ScheduleEntity?

    @Query("SELECT * FROM schedules ORDER BY createdAt ASC")
    suspend fun getAll(): List<ScheduleEntity>

    @Query("SELECT COUNT(*) FROM schedules")
    suspend fun count(): Int

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Delete
    suspend fun delete(schedule: ScheduleEntity)

    @Query("UPDATE schedules SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: Long)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE scheduleId = :scheduleId ORDER BY startMinute ASC")
    fun observeForSchedule(scheduleId: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE scheduleId = :scheduleId ORDER BY startMinute ASC")
    suspend fun getForSchedule(scheduleId: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntity?

    @Query("SELECT * FROM activities")
    suspend fun getAll(): List<ActivityEntity>

    @Query("UPDATE activities SET tracked = :tracked WHERE id = :id")
    suspend fun setTracked(id: Long, tracked: Boolean)

    @Query("SELECT scheduleId, COUNT(*) AS count FROM activities GROUP BY scheduleId")
    fun observeCounts(): Flow<List<ScheduleCount>>

    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Insert
    suspend fun insertAll(activities: List<ActivityEntity>)

    @Update
    suspend fun update(activity: ActivityEntity)

    @Delete
    suspend fun delete(activity: ActivityEntity)

    @Query("DELETE FROM activities WHERE scheduleId = :scheduleId")
    suspend fun deleteForSchedule(scheduleId: Long)

    @Query("DELETE FROM activities")
    suspend fun deleteAll()
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions WHERE epochDay >= :fromDay")
    fun observeSince(fromDay: Long): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE epochDay BETWEEN :fromDay AND :toDay")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE activityId = :activityId")
    suspend fun getForActivity(activityId: Long): List<CompletionEntity>

    @Query("SELECT COUNT(*) FROM completions WHERE activityId = :activityId AND epochDay = :epochDay")
    suspend fun count(activityId: Long, epochDay: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(completion: CompletionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(completions: List<CompletionEntity>)

    @Query("DELETE FROM completions WHERE activityId = :activityId AND epochDay = :epochDay")
    suspend fun delete(activityId: Long, epochDay: Long)

    @Query("DELETE FROM completions WHERE activityId IN (SELECT id FROM activities WHERE scheduleId = :scheduleId)")
    suspend fun deleteForSchedule(scheduleId: Long)

    @Query("DELETE FROM completions WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: Long)

    @Query("DELETE FROM completions")
    suspend fun deleteAll()
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM day_overrides WHERE scheduleId = :scheduleId AND epochDay BETWEEN :fromDay AND :toDay ORDER BY id ASC")
    fun observeRange(scheduleId: Long, fromDay: Long, toDay: Long): Flow<List<OverrideEntity>>

    @Query("SELECT * FROM day_overrides WHERE scheduleId = :scheduleId AND epochDay BETWEEN :fromDay AND :toDay ORDER BY id ASC")
    suspend fun getRange(scheduleId: Long, fromDay: Long, toDay: Long): List<OverrideEntity>

    @Insert
    suspend fun insert(override: OverrideEntity): Long

    @Query("DELETE FROM day_overrides WHERE scheduleId = :scheduleId AND epochDay = :epochDay AND type = 0 AND activityId = :activityId")
    suspend fun deleteSkip(scheduleId: Long, epochDay: Long, activityId: Long)

    @Query("DELETE FROM day_overrides WHERE scheduleId = :scheduleId AND epochDay = :epochDay AND type = 0 AND activityId IS NULL")
    suspend fun deleteDayOff(scheduleId: Long, epochDay: Long)

    @Query("DELETE FROM day_overrides WHERE scheduleId = :scheduleId AND epochDay = :epochDay AND type = 1")
    suspend fun deleteShifts(scheduleId: Long, epochDay: Long)

    @Query("DELETE FROM day_overrides WHERE scheduleId = :scheduleId")
    suspend fun deleteForSchedule(scheduleId: Long)

    @Query("DELETE FROM day_overrides WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: Long)

    @Query("DELETE FROM day_overrides WHERE epochDay < :beforeDay")
    suspend fun deleteOlderThan(beforeDay: Long)

    @Query("DELETE FROM day_overrides")
    suspend fun deleteAll()
}
