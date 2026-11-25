package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.GachaLog
import io.github.takosasidev.altroster.core.model.GameAccount
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.core.model.TaskCheckIn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val JST: ZoneId = ZoneId.of("Asia/Tokyo")

/** 日本時間の壁時計の時刻を Instant にする。 */
fun jst(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
    LocalDateTime.of(year, month, day, hour, minute).atZone(JST).toInstant()

fun title(id: Long, name: String = "タイトル$id", archived: Boolean = false, tags: List<String> = emptyList(), sortOrder: Int = id.toInt()) =
    GameTitle(id = id, name = name, colorArgb = 0xFF3F51B5.toInt(), sortOrder = sortOrder, isArchived = archived, tags = tags)

fun account(id: Long, titleId: Long, label: String = "メイン", archived: Boolean = false, createdAt: Instant = jst(2026, 1, 1)) =
    GameAccount(id = id, gameTitleId = titleId, label = label, createdAt = createdAt, isArchived = archived)

fun stamina(
    id: Long,
    accountId: Long,
    max: Int = 100,
    interval: Int = 5,
    value: Int = 50,
    at: Instant = jst(2026, 8, 25, 14, 0),
    notifyOnFull: Boolean = true,
    threshold: Int? = null,
    name: String = "AP",
) = StaminaResource(
    id = id, gameAccountId = accountId, resourceName = name, maxValue = max, recoveryIntervalMinutes = interval,
    recordedValue = value, recordedAt = at, notifyOnFull = notifyOnFull, notifyThresholdPercent = threshold,
)

fun event(id: Long, titleId: Long, endAt: Instant, hours: Int = 24, completed: Boolean = false, name: String = "イベント$id") =
    GameEvent(id = id, gameTitleId = titleId, name = name, endAt = endAt, reminderBeforeHours = hours, isCompleted = completed)

fun daily(id: Long, accountId: Long, time: String = "05:00", label: String = "デイリー$id") =
    DailyTask(id = id, gameAccountId = accountId, label = label, resetType = ResetType.DAILY, resetTime = time)

fun weekly(id: Long, accountId: Long, time: String = "1 05:00", label: String = "ウィークリー$id") =
    DailyTask(id = id, gameAccountId = accountId, label = label, resetType = ResetType.WEEKLY, resetTime = time)

fun checkIn(id: Long, taskId: Long, periodKey: String, checked: Boolean = true, at: Instant? = null) =
    TaskCheckIn(id = id, dailyTaskId = taskId, periodKey = periodKey, isChecked = checked, checkedAt = at)

fun gacha(id: Long, accountId: Long, date: LocalDate, amount: Int, memo: String? = null) =
    GachaLog(id = id, gameAccountId = accountId, date = date, amount = amount, memo = memo, createdAt = jst(2026, 1, 1))
