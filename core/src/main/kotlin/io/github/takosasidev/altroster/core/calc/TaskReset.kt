package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.Roster
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** タスクのリセットの決まり。DB には [encode] の文字列 1 つで入れる。 */
data class ResetSpec(
    val type: ResetType,
    val time: LocalTime,
    /** 毎週のときだけ使う。 */
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
) {
    /** 毎日は "05:00"、毎週は "1 05:00"(1=月〜7=日)。 */
    fun encode(): String {
        val hhmm = AppSettings.formatTime(time.hour, time.minute)
        return if (type == ResetType.WEEKLY) "${dayOfWeek.value} $hhmm" else hhmm
    }

    companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(5, 0)

        /** 読めない文字列でも落とさず、5:00(毎週は月曜)として扱う。 */
        fun parse(type: ResetType, raw: String): ResetSpec {
            val parts = raw.trim().split(" ").filter { it.isNotEmpty() }
            val time = parts.lastOrNull()?.let(AppSettings::parseTime) ?: DEFAULT_TIME
            val day = if (parts.size >= 2) {
                parts[0].toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of)
            } else {
                null
            }
            return ResetSpec(type, time, day ?: DayOfWeek.MONDAY)
        }

        fun of(task: DailyTask): ResetSpec = parse(task.resetType, task.resetTime)
    }
}

/**
 * デイリー・ウィークリーのリセットの時刻と、今がどの期間かの判定。
 *
 * リセットの時刻は、端末の時間帯の壁時計で数える(利用者が打ち込んだ時刻をそのまま使う)。
 * 時間帯は引数で受け取るので、旅行で時間帯が変わっても、その時の時間帯で数え直すだけで済む。
 * チェックの印を消す処理は無い。期間の名前([periodKey])が変わると、前の期間の印が当たらなくなる。
 */
object TaskReset {

    /** [now] の直前(ちょうども含む)のリセット。 */
    fun lastResetAt(spec: ResetSpec, now: Instant, zone: ZoneId): ZonedDateTime {
        val today = now.atZone(zone).toLocalDate()
        val step = if (spec.type == ResetType.WEEKLY) 7L else 1L
        var date: LocalDate = when (spec.type) {
            ResetType.DAILY -> today
            ResetType.WEEKLY -> today.with(TemporalAdjusters.previousOrSame(spec.dayOfWeek))
        }
        // 夏時間の切り替えで存在しない時刻は、ZonedDateTime.of が後ろへずらす
        var at = ZonedDateTime.of(date, spec.time, zone)
        if (at.toInstant().isAfter(now)) {
            date = date.minusDays(step)
            at = ZonedDateTime.of(date, spec.time, zone)
        }
        return at
    }

    /** [now] より後の、次のリセット。 */
    fun nextResetAt(spec: ResetSpec, now: Instant, zone: ZoneId): Instant {
        val step = if (spec.type == ResetType.WEEKLY) 7L else 1L
        val last = lastResetAt(spec, now, zone)
        return ZonedDateTime.of(last.toLocalDate().plusDays(step), spec.time, zone).toInstant()
    }

    /** 今の期間の名前。直前のリセットの日付(ISO)。 */
    fun periodKey(spec: ResetSpec, now: Instant, zone: ZoneId): String =
        lastResetAt(spec, now, zone).toLocalDate().toString()

    fun periodKey(task: DailyTask, now: Instant, zone: ZoneId): String =
        periodKey(ResetSpec.of(task), now, zone)

    /** 今の期間でチェック済みのタスクの id。 */
    fun checkedTaskIds(roster: Roster, now: Instant, zone: ZoneId): Set<Long> {
        val checked = roster.checkIns.filter { it.isChecked }.groupBy({ it.dailyTaskId }, { it.periodKey })
        return roster.tasks
            .filter { task -> checked[task.id]?.contains(periodKey(task, now, zone)) == true }
            .map { it.id }
            .toSet()
    }
}
