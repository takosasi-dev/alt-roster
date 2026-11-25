package io.github.takosasidev.altroster.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 集計と判定は :core が行うので、DAO は表ごとの取り出しと書き込みだけを持つ。
 *
 * 保存は @Upsert を使う。@Insert(REPLACE) は「消してから入れ直す」動きなので、
 * 外部キーの CASCADE で子の行まで消えてしまう。
 * @Upsert の戻り値は、新しく入れたときは行の id、更新だったときは -1。
 */
@Dao
interface RosterDao {
    @Query("SELECT * FROM game_title ORDER BY sortOrder, id")
    fun observeTitles(): Flow<List<GameTitleEntity>>

    @Query("SELECT * FROM game_account ORDER BY id")
    fun observeAccounts(): Flow<List<GameAccountEntity>>

    @Query("SELECT * FROM stamina_resource ORDER BY id")
    fun observeStaminas(): Flow<List<StaminaResourceEntity>>

    @Query("SELECT * FROM game_event ORDER BY endAtEpochMilli, id")
    fun observeEvents(): Flow<List<GameEventEntity>>

    @Query("SELECT * FROM daily_task ORDER BY sortOrder, id")
    fun observeTasks(): Flow<List<DailyTaskEntity>>

    @Query("SELECT * FROM task_check_in ORDER BY id")
    fun observeCheckIns(): Flow<List<TaskCheckInEntity>>

    @Query("SELECT * FROM gacha_log ORDER BY date DESC, createdAtEpochMilli DESC, id DESC")
    fun observeGachaLogs(): Flow<List<GachaLogEntity>>

    @Upsert suspend fun upsert(row: GameTitleEntity): Long
    @Upsert suspend fun upsert(row: GameAccountEntity): Long
    @Upsert suspend fun upsert(row: StaminaResourceEntity): Long
    @Upsert suspend fun upsert(row: GameEventEntity): Long
    @Upsert suspend fun upsert(row: DailyTaskEntity): Long
    @Upsert suspend fun upsert(row: TaskCheckInEntity): Long
    @Upsert suspend fun upsert(row: GachaLogEntity): Long

    @Query("SELECT * FROM task_check_in WHERE dailyTaskId = :taskId AND periodKey = :periodKey LIMIT 1")
    suspend fun findCheckIn(taskId: Long, periodKey: String): TaskCheckInEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM game_title")
    suspend fun maxTitleSortOrder(): Int

    @Query("UPDATE game_title SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setTitleSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM game_title WHERE id = :id") suspend fun deleteTitle(id: Long)
    @Query("DELETE FROM game_account WHERE id = :id") suspend fun deleteAccount(id: Long)
    @Query("DELETE FROM stamina_resource WHERE id = :id") suspend fun deleteStamina(id: Long)
    @Query("DELETE FROM game_event WHERE id = :id") suspend fun deleteEvent(id: Long)
    @Query("DELETE FROM daily_task WHERE id = :id") suspend fun deleteTask(id: Long)
    @Query("DELETE FROM gacha_log WHERE id = :id") suspend fun deleteGachaLog(id: Long)

    // ---- バックアップの取り込み用 -------------------------------------------
    // タイトルを全部消すと、外部キーの CASCADE でほかの表も空になる。

    @Query("DELETE FROM game_title") suspend fun deleteAllTitles()

    @Insert suspend fun insertTitles(rows: List<GameTitleEntity>)
    @Insert suspend fun insertAccounts(rows: List<GameAccountEntity>)
    @Insert suspend fun insertStaminas(rows: List<StaminaResourceEntity>)
    @Insert suspend fun insertEvents(rows: List<GameEventEntity>)
    @Insert suspend fun insertTasks(rows: List<DailyTaskEntity>)
    @Insert suspend fun insertCheckIns(rows: List<TaskCheckInEntity>)
    @Insert suspend fun insertGachaLogs(rows: List<GachaLogEntity>)
}
