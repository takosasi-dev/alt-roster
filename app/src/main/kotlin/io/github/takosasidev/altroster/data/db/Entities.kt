package io.github.takosasidev.altroster.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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

// 日時はエポックのミリ秒、日付は ISO の文字列("2026-08-24")で持つ。
// 親を消すと子も消える(外部キーの CASCADE)。確認の画面で件数を見せてから消す。

@Entity(tableName = "game_title")
data class GameTitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val iconKey: String?,
    val colorArgb: Int,
    val sortOrder: Int,
    val isArchived: Boolean,
    /** タグをカンマ区切りで持つ。タグ自体からカンマは落としてある。 */
    val tags: String?,
)

@Entity(
    tableName = "game_account",
    foreignKeys = [
        ForeignKey(
            entity = GameTitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameTitleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameTitleId")],
)
data class GameAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val gameTitleId: Long,
    val label: String,
    val loginHintMemo: String?,
    val createdAtEpochMilli: Long,
    val isArchived: Boolean,
)

@Entity(
    tableName = "stamina_resource",
    foreignKeys = [
        ForeignKey(
            entity = GameAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameAccountId")],
)
data class StaminaResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val gameAccountId: Long,
    val resourceName: String,
    val maxValue: Int,
    val recoveryIntervalMinutes: Int,
    val recordedValue: Int,
    val recordedAtEpochMilli: Long,
    val notifyOnFull: Boolean,
    val notifyThresholdPercent: Int?,
)

@Entity(
    tableName = "game_event",
    foreignKeys = [
        ForeignKey(
            entity = GameTitleEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameTitleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameTitleId")],
)
data class GameEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val gameTitleId: Long,
    val name: String,
    val endAtEpochMilli: Long,
    val reminderBeforeHours: Int,
    val isCompleted: Boolean,
)

@Entity(
    tableName = "daily_task",
    foreignKeys = [
        ForeignKey(
            entity = GameAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameAccountId")],
)
data class DailyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val gameAccountId: Long,
    val label: String,
    val resetType: String,
    val resetTime: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "task_check_in",
    foreignKeys = [
        ForeignKey(
            entity = DailyTaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["dailyTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    // 1 つのタスクの 1 つの期間に、記録は 1 行だけ
    indices = [Index(value = ["dailyTaskId", "periodKey"], unique = true)],
)
data class TaskCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val dailyTaskId: Long,
    val periodKey: String,
    val isChecked: Boolean,
    val checkedAtEpochMilli: Long?,
)

@Entity(
    tableName = "gacha_log",
    foreignKeys = [
        ForeignKey(
            entity = GameAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("gameAccountId"), Index("date")],
)
data class GachaLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val gameAccountId: Long,
    val date: String,
    val amount: Int,
    val currencyType: String?,
    val memo: String?,
    val createdAtEpochMilli: Long,
)

// ---- :core の型との変換 -----------------------------------------------------

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

fun GameTitleEntity.toModel() = GameTitle(
    id = id,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    isArchived = isArchived,
    tags = GameTitle.normalizeTags(tags?.split(",").orEmpty()),
)

fun GameTitle.toEntity() = GameTitleEntity(
    id = id,
    name = name.trim(),
    iconKey = iconKey.clean(),
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    isArchived = isArchived,
    tags = GameTitle.normalizeTags(tags).takeIf { it.isNotEmpty() }?.joinToString(","),
)

fun GameAccountEntity.toModel() = GameAccount(
    id = id,
    gameTitleId = gameTitleId,
    label = label,
    loginHintMemo = loginHintMemo,
    createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
    isArchived = isArchived,
)

fun GameAccount.toEntity() = GameAccountEntity(
    id = id,
    gameTitleId = gameTitleId,
    label = label.trim(),
    loginHintMemo = loginHintMemo.clean(),
    createdAtEpochMilli = createdAt.toEpochMilli(),
    isArchived = isArchived,
)

fun StaminaResourceEntity.toModel() = StaminaResource(
    id = id,
    gameAccountId = gameAccountId,
    resourceName = resourceName,
    maxValue = maxValue,
    recoveryIntervalMinutes = recoveryIntervalMinutes,
    recordedValue = recordedValue,
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMilli),
    notifyOnFull = notifyOnFull,
    notifyThresholdPercent = notifyThresholdPercent,
)

fun StaminaResource.toEntity() = StaminaResourceEntity(
    id = id,
    gameAccountId = gameAccountId,
    resourceName = resourceName.trim(),
    maxValue = maxValue,
    recoveryIntervalMinutes = recoveryIntervalMinutes,
    recordedValue = recordedValue,
    recordedAtEpochMilli = recordedAt.toEpochMilli(),
    notifyOnFull = notifyOnFull,
    notifyThresholdPercent = notifyThresholdPercent,
)

fun GameEventEntity.toModel() = GameEvent(
    id = id,
    gameTitleId = gameTitleId,
    name = name,
    endAt = Instant.ofEpochMilli(endAtEpochMilli),
    reminderBeforeHours = reminderBeforeHours,
    isCompleted = isCompleted,
)

fun GameEvent.toEntity() = GameEventEntity(
    id = id,
    gameTitleId = gameTitleId,
    name = name.trim(),
    endAtEpochMilli = endAt.toEpochMilli(),
    reminderBeforeHours = reminderBeforeHours,
    isCompleted = isCompleted,
)

fun DailyTaskEntity.toModel() = DailyTask(
    id = id,
    gameAccountId = gameAccountId,
    label = label,
    resetType = runCatching { ResetType.valueOf(resetType) }.getOrDefault(ResetType.DAILY),
    resetTime = resetTime,
    sortOrder = sortOrder,
)

fun DailyTask.toEntity() = DailyTaskEntity(
    id = id,
    gameAccountId = gameAccountId,
    label = label.trim(),
    resetType = resetType.name,
    resetTime = resetTime,
    sortOrder = sortOrder,
)

fun TaskCheckInEntity.toModel() = TaskCheckIn(
    id = id,
    dailyTaskId = dailyTaskId,
    periodKey = periodKey,
    isChecked = isChecked,
    checkedAt = checkedAtEpochMilli?.let(Instant::ofEpochMilli),
)

fun TaskCheckIn.toEntity() = TaskCheckInEntity(
    id = id,
    dailyTaskId = dailyTaskId,
    periodKey = periodKey,
    isChecked = isChecked,
    checkedAtEpochMilli = checkedAt?.toEpochMilli(),
)

fun GachaLogEntity.toModel() = GachaLog(
    id = id,
    gameAccountId = gameAccountId,
    date = LocalDate.parse(date),
    amount = amount,
    currencyType = currencyType,
    memo = memo,
    createdAt = Instant.ofEpochMilli(createdAtEpochMilli),
)

fun GachaLog.toEntity() = GachaLogEntity(
    id = id,
    gameAccountId = gameAccountId,
    date = date.toString(),
    amount = amount,
    currencyType = currencyType.clean(),
    memo = memo.clean(),
    createdAtEpochMilli = createdAt.toEpochMilli(),
)
