package io.github.takosasidev.altroster.data

import android.content.Context
import androidx.room.withTransaction
import io.github.takosasidev.altroster.core.calc.TaskReset
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.GachaLog
import io.github.takosasidev.altroster.core.model.GameAccount
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.Roster
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.core.model.TaskCheckIn
import io.github.takosasidev.altroster.data.db.AppDatabase
import io.github.takosasidev.altroster.data.db.toEntity
import io.github.takosasidev.altroster.data.db.toModel
import io.github.takosasidev.altroster.notify.AlarmScheduler
import io.github.takosasidev.altroster.widget.RosterWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 画面・通知・ウィジェットが使う、ただ 1 つのデータの入口。
 *
 * 書き込みのあとは必ず [changed] を通し、次のアラームとウィジェットを今の記録に合わせ直す。
 * 時刻は [clock] から取る(テストで差し替えられるように)。時間帯は、旅行などで途中で変わるので、
 * 使うたびに端末の設定を読む。
 */
class RosterRepository(context: Context, val clock: Clock = Clock.systemUTC()) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val dao = db.rosterDao()
    val settingsStore = SettingsStore(appContext)

    fun now(): Instant = clock.instant()
    fun zone(): ZoneId = ZoneId.systemDefault()

    val settings: Flow<AppSettings> = settingsStore.settings

    val roster: Flow<Roster> = combine(
        combine(
            dao.observeTitles(),
            dao.observeAccounts(),
            dao.observeStaminas(),
            dao.observeEvents(),
        ) { titles, accounts, staminas, events ->
            Roster(
                titles = titles.map { it.toModel() },
                accounts = accounts.map { it.toModel() },
                staminas = staminas.map { it.toModel() },
                events = events.map { it.toModel() },
            )
        },
        dao.observeTasks(),
        dao.observeCheckIns(),
        dao.observeGachaLogs(),
    ) { partial, tasks, checkIns, gachaLogs ->
        partial.copy(
            tasks = tasks.map { it.toModel() },
            checkIns = checkIns.map { it.toModel() },
            gachaLogs = gachaLogs.map { it.toModel() },
        )
    }

    suspend fun rosterOnce(): Roster = roster.first()

    // ---- 書き込み ----------------------------------------------------------

    /** 新しいタイトルは、並びのいちばん後ろに入れる。 */
    suspend fun saveTitle(title: GameTitle): Long = write {
        val row = if (title.id == 0L) title.copy(sortOrder = dao.maxTitleSortOrder() + 1) else title
        dao.upsert(row.toEntity()).let { if (it > 0) it else title.id }
    }

    /** 表示の順を 1 つ上か下へ動かす。アーカイブしていないタイトルの中で入れ替える。 */
    suspend fun moveTitle(titleId: Long, up: Boolean) = write {
        val order = rosterOnce().activeTitles.toMutableList()
        val from = order.indexOfFirst { it.id == titleId }
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in order.indices) return@write
        order.add(to, order.removeAt(from))
        db.withTransaction { order.forEachIndexed { index, t -> dao.setTitleSortOrder(t.id, index + 1) } }
    }

    suspend fun deleteTitle(id: Long) = write { dao.deleteTitle(id) }

    suspend fun saveAccount(account: GameAccount): Long = write {
        dao.upsert(account.toEntity()).let { if (it > 0) it else account.id }
    }

    suspend fun deleteAccount(id: Long) = write { dao.deleteAccount(id) }

    suspend fun saveStamina(resource: StaminaResource) = write { dao.upsert(resource.toEntity()) }
    suspend fun deleteStamina(id: Long) = write { dao.deleteStamina(id) }

    suspend fun saveEvent(event: GameEvent) = write { dao.upsert(event.toEntity()) }
    suspend fun deleteEvent(id: Long) = write { dao.deleteEvent(id) }

    suspend fun saveTask(task: DailyTask) = write { dao.upsert(task.toEntity()) }
    suspend fun deleteTask(id: Long) = write { dao.deleteTask(id) }

    /**
     * 今の期間のチェックを付ける・外す。期間ごとに 1 行を持ち、外したときも行は残す
     * (リセットのたびに行を消さないので、過去の期間の記録が集計に使える)。
     */
    suspend fun setTaskChecked(task: DailyTask, checked: Boolean) = write {
        val now = now()
        val key = TaskReset.periodKey(task, now, zone())
        val existing = dao.findCheckIn(task.id, key)?.toModel()
        val row = (existing ?: TaskCheckIn(dailyTaskId = task.id, periodKey = key, isChecked = checked))
            .copy(isChecked = checked, checkedAt = if (checked) now else null)
        dao.upsert(row.toEntity())
    }

    suspend fun saveGachaLog(log: GachaLog) = write { dao.upsert(log.toEntity()) }
    suspend fun deleteGachaLog(id: Long) = write { dao.deleteGachaLog(id) }

    /** 設定を変える。通知の時刻に関わる設定があるので、アラームも合わせ直す。 */
    suspend fun updateSettings(block: suspend SettingsStore.() -> Unit) = write { settingsStore.block() }

    /** バックアップの取り込み用。今の記録を全部捨ててから書き戻す。途中で失敗したら元のまま。 */
    suspend fun replaceAll(roster: Roster, settings: AppSettings) = write {
        db.withTransaction {
            dao.deleteAllTitles()
            // 親 → 子の順に入れる(外部キー)
            dao.insertTitles(roster.titles.map { it.toEntity() })
            dao.insertAccounts(roster.accounts.map { it.toEntity() })
            dao.insertStaminas(roster.staminas.map { it.toEntity() })
            dao.insertEvents(roster.events.map { it.toEntity() })
            dao.insertTasks(roster.tasks.map { it.toEntity() })
            dao.insertCheckIns(roster.checkIns.map { it.toEntity() })
            dao.insertGachaLogs(roster.gachaLogs.map { it.toEntity() })
        }
        settingsStore.replaceAll(settings)
    }

    private suspend fun <T> write(block: suspend () -> T): T = block().also { changed() }

    /** アラームやウィジェットの更新に失敗しても、記録の保存は成功のままにする。 */
    private suspend fun changed() {
        runCatching { AlarmScheduler.arm(appContext) }
        runCatching { RosterWidget.refresh(appContext) }
    }

    companion object {
        @Volatile
        private var instance: RosterRepository? = null

        fun get(context: Context): RosterRepository = instance ?: synchronized(this) {
            instance ?: RosterRepository(context.applicationContext).also { instance = it }
        }
    }
}
