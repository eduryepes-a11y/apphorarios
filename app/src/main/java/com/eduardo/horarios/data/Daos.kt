package com.eduardo.horarios.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
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
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE scheduleId = :scheduleId ORDER BY startMinute ASC")
    fun observeForSchedule(scheduleId: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE scheduleId = :scheduleId ORDER BY startMinute ASC")
    suspend fun getForSchedule(scheduleId: Long): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntity?

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
}
