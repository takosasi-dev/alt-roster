package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.Roster
import java.time.Instant
import java.time.ZoneId

/** 出す通知 1 件。[at] は、出すと決めた時刻(実際に鳴る時刻は端末の都合で遅れることがある)。 */
sealed interface Notice {
    val at: Instant

    data class StaminaFull(override val at: Instant, val resourceId: Long) : Notice
    data class StaminaThreshold(override val at: Instant, val resourceId: Long, val percent: Int) : Notice
    data class EventReminder(override val at: Instant, val eventId: Long) : Notice

    /** 同じ時にリセットされたタスクは 1 件の通知にまとめる。 */
    data class TasksReset(override val at: Instant, val taskIds: List<Long>) : Notice

    /** 1 日 1 回のまとめの時刻。中身はその時に DigestBuilder で作り、空なら出さない。 */
    data class DigestTime(override val at: Instant) : Notice
}

/**
 * 通知を出す時刻の決め方。
 *
 * 端末には「次に何かが起きる時刻」のアラームを 1 本だけ仕込む([nextTriggerAfter])。
 * 鳴ったら、前に処理した時刻から今までに出すはずだった物を全部出す([dueBetween])。
 * 再起動や省電力でアラームが遅れても、この範囲に入るので取りこぼさない。
 * アーカイブしたタイトル・アカウントの物は対象にしない。
 */
object NoticePlan {

    /** [after] より後で [until] 以前に出すはずだった通知。時刻の順。 */
    fun dueBetween(
        roster: Roster,
        settings: AppSettings,
        after: Instant,
        until: Instant,
        zone: ZoneId,
    ): List<Notice> {
        if (!until.isAfter(after)) return emptyList()
        fun inRange(at: Instant?): Boolean = at != null && at.isAfter(after) && !at.isAfter(until)

        val result = mutableListOf<Notice>()

        roster.activeStaminas.forEach { r ->
            val fullAt = Stamina.fullNoticeAt(r)
            val thresholdAt = Stamina.thresholdAt(r)
            when {
                inRange(fullAt) -> result += Notice.StaminaFull(fullAt!!, r.id)
                // 満タンも同じ範囲に入った(長く止まっていた)なら、途中の割合の通知は出さない
                inRange(thresholdAt) ->
                    result += Notice.StaminaThreshold(thresholdAt!!, r.id, r.notifyThresholdPercent ?: 0)
            }
        }

        roster.activeEvents.forEach { e ->
            val at = EventTime.reminderAt(e)
            // もう終わったイベントを「もうすぐ終わる」と知らせない
            if (!e.isCompleted && inRange(at) && e.endAt.isAfter(until)) result += Notice.EventReminder(at, e.id)
        }

        if (settings.resetNotificationEnabled) {
            val resets = roster.activeTasks.mapNotNull { task ->
                val last = TaskReset.lastResetAt(ResetSpec.of(task), until, zone).toInstant()
                if (last.isAfter(after)) task.id to last else null
            }
            if (resets.isNotEmpty()) {
                result += Notice.TasksReset(resets.maxOf { it.second }, resets.map { it.first })
            }
        }

        if (settings.digestNotificationEnabled) {
            // 何日も止まっていても、まとめは直近の 1 回だけ
            val last = TaskReset.lastResetAt(digestSpec(settings), until, zone).toInstant()
            if (last.isAfter(after)) result += Notice.DigestTime(last)
        }

        return result.sortedBy { it.at }
    }

    /** [now] より後で、いちばん早く何かを出す時刻。何も無ければ null(アラームを仕込まない)。 */
    fun nextTriggerAfter(roster: Roster, settings: AppSettings, now: Instant, zone: ZoneId): Instant? {
        val candidates = mutableListOf<Instant?>()
        roster.activeStaminas.forEach { r ->
            candidates += Stamina.fullNoticeAt(r)
            candidates += Stamina.thresholdAt(r)
        }
        roster.activeEvents.filter { !it.isCompleted }.forEach { candidates += EventTime.reminderAt(it) }
        if (settings.resetNotificationEnabled) {
            roster.activeTasks.forEach { candidates += TaskReset.nextResetAt(ResetSpec.of(it), now, zone) }
        }
        if (settings.digestNotificationEnabled) {
            candidates += TaskReset.nextResetAt(digestSpec(settings), now, zone)
        }
        return candidates.filterNotNull().filter { it.isAfter(now) }.minOrNull()
    }

    /** まとめの通知は「毎日決まった時刻」なので、デイリーのリセットと同じ数え方を使う。 */
    private fun digestSpec(settings: AppSettings) = ResetSpec(ResetType.DAILY, settings.digestTime)
}
