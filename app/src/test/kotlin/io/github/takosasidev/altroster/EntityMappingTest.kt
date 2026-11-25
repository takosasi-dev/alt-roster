package io.github.takosasidev.altroster

import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.GachaLog
import io.github.takosasidev.altroster.core.model.GameAccount
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.core.model.TaskCheckIn
import io.github.takosasidev.altroster.data.db.DailyTaskEntity
import io.github.takosasidev.altroster.data.db.toEntity
import io.github.takosasidev.altroster.data.db.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** DB の行と :core の型の変換が、行って戻って同じになること。 */
class EntityMappingTest {
    private val at: Instant = Instant.parse("2026-08-25T05:00:00Z")

    @Test
    fun タイトルは往復で同じ_タグはカンマ区切りの1列になる() {
        val title = GameTitle(id = 3, name = "タイトルA", iconKey = "star", colorArgb = -12627531, sortOrder = 2, isArchived = true, tags = listOf("RPG", "優先"))
        assertEquals("RPG,優先", title.toEntity().tags)
        assertEquals(title, title.toEntity().toModel())
        // タグが無ければ null(空の文字列を入れない)
        assertNull(title.copy(tags = emptyList()).toEntity().tags)
    }

    @Test
    fun ほかの表も往復で同じ() {
        val account = GameAccount(id = 1, gameTitleId = 3, label = "サブ", loginHintMemo = "メールで連携", createdAt = at, isArchived = true)
        val stamina = StaminaResource(id = 2, gameAccountId = 1, resourceName = "AP", maxValue = 160, recoveryIntervalMinutes = 6, recordedValue = 40, recordedAt = at, notifyOnFull = false, notifyThresholdPercent = 90)
        val event = GameEvent(id = 4, gameTitleId = 3, name = "イベントX", endAt = at, reminderBeforeHours = 6, isCompleted = true)
        val task = DailyTask(id = 5, gameAccountId = 1, label = "ウィークリー", resetType = ResetType.WEEKLY, resetTime = "4 04:00", sortOrder = 1)
        val checkIn = TaskCheckIn(id = 6, dailyTaskId = 5, periodKey = "2026-08-20", isChecked = true, checkedAt = at)
        val log = GachaLog(id = 7, gameAccountId = 1, date = LocalDate.of(2026, 8, 1), amount = 3000, currencyType = "石", memo = "10 連", createdAt = at)

        assertEquals(account, account.toEntity().toModel())
        assertEquals(stamina, stamina.toEntity().toModel())
        assertEquals(event, event.toEntity().toModel())
        assertEquals(task, task.toEntity().toModel())
        assertEquals(checkIn, checkIn.toEntity().toModel())
        assertEquals(log, log.toEntity().toModel())
    }

    @Test
    fun 空白だけのメモは_null_として保存する() {
        val account = GameAccount(gameTitleId = 1, label = " メイン ", loginHintMemo = "   ", createdAt = at)
        assertNull(account.toEntity().loginHintMemo)
        assertEquals("メイン", account.toEntity().label)
    }

    @Test
    fun 知らないリセットの種別は_毎日として読む() {
        val row = DailyTaskEntity(id = 1, gameAccountId = 1, label = "x", resetType = "MONTHLY", resetTime = "05:00", sortOrder = 0)
        assertEquals(ResetType.DAILY, row.toModel().resetType)
    }
}
