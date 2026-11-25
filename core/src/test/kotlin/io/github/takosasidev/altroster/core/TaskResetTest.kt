package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.calc.ResetSpec
import io.github.takosasidev.altroster.core.calc.TaskReset
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.Roster
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskResetTest {
    private val daily5 = ResetSpec(ResetType.DAILY, LocalTime.of(5, 0))

    @Test
    fun リセットの時刻より前は前の日の期間_ちょうどからは今日の期間() {
        assertEquals("2026-08-24", TaskReset.periodKey(daily5, jst(2026, 8, 25, 4, 59), JST))
        assertEquals("2026-08-25", TaskReset.periodKey(daily5, jst(2026, 8, 25, 5, 0), JST))
        assertEquals("2026-08-25", TaskReset.periodKey(daily5, jst(2026, 8, 26, 4, 59), JST))
    }

    @Test
    fun 次のリセットは今より後() {
        assertEquals(jst(2026, 8, 25, 5, 0), TaskReset.nextResetAt(daily5, jst(2026, 8, 25, 4, 59), JST))
        assertEquals(jst(2026, 8, 26, 5, 0), TaskReset.nextResetAt(daily5, jst(2026, 8, 25, 5, 0), JST))
    }

    @Test
    fun 要件定義書の例_今日チェックしたタスクは_リセットの時刻を過ぎると未チェックに戻る() {
        val task = daily(1, 1, time = "05:00")
        val checkedAt = Clock.fixed(jst(2026, 8, 25, 22, 0), JST)
        val key = TaskReset.periodKey(task, checkedAt.instant(), JST)
        val roster = Roster(tasks = listOf(task), checkIns = listOf(checkIn(1, 1, key)))

        assertTrue(1L in TaskReset.checkedTaskIds(roster, jst(2026, 8, 26, 4, 59), JST))
        assertFalse(1L in TaskReset.checkedTaskIds(roster, jst(2026, 8, 26, 5, 0), JST))
    }

    @Test
    fun 何日もアプリを開かなくても_開いた時の期間で判定する() {
        val task = daily(1, 1)
        val roster = Roster(tasks = listOf(task), checkIns = listOf(checkIn(1, 1, "2026-08-20")))
        assertTrue(TaskReset.checkedTaskIds(roster, jst(2026, 8, 30, 12, 0), JST).isEmpty())
    }

    @Test
    fun チェックを外した記録は_チェック済みに数えない() {
        val roster = Roster(tasks = listOf(daily(1, 1)), checkIns = listOf(checkIn(1, 1, "2026-08-25", checked = false)))
        assertTrue(TaskReset.checkedTaskIds(roster, jst(2026, 8, 25, 12, 0), JST).isEmpty())
    }

    @Test
    fun 毎週のリセット_月曜5時() {
        val spec = ResetSpec(ResetType.WEEKLY, LocalTime.of(5, 0), DayOfWeek.MONDAY)
        // 2026-08-24 は月曜
        assertEquals("2026-08-17", TaskReset.periodKey(spec, jst(2026, 8, 24, 4, 59), JST))
        assertEquals("2026-08-24", TaskReset.periodKey(spec, jst(2026, 8, 24, 5, 0), JST))
        assertEquals("2026-08-24", TaskReset.periodKey(spec, jst(2026, 8, 30, 23, 59), JST))
        assertEquals(jst(2026, 8, 31, 5, 0), TaskReset.nextResetAt(spec, jst(2026, 8, 27, 12, 0), JST))
    }

    @Test
    fun 毎週のタスクは_その週のあいだチェック済みのまま() {
        val task = weekly(1, 1, time = "1 05:00")
        val roster = Roster(tasks = listOf(task), checkIns = listOf(checkIn(1, 1, "2026-08-24")))
        assertTrue(1L in TaskReset.checkedTaskIds(roster, jst(2026, 8, 30, 12, 0), JST))
        assertFalse(1L in TaskReset.checkedTaskIds(roster, jst(2026, 8, 31, 5, 0), JST))
    }

    @Test
    fun 時差_同じ瞬間でも_時間帯が違えば期間が違う() {
        // 日本時間 8/25 05:30 は、ロサンゼルスでは 8/24 13:30
        val now = jst(2026, 8, 25, 5, 30)
        assertEquals("2026-08-25", TaskReset.periodKey(daily5, now, JST))
        assertEquals("2026-08-24", TaskReset.periodKey(daily5, now, ZoneId.of("America/Los_Angeles")))
        // UTC では 8/24 20:30。5 時は過ぎているので 8/24 の期間
        assertEquals("2026-08-24", TaskReset.periodKey(daily5, now, ZoneId.of("UTC")))
    }

    @Test
    fun 夏時間で存在しない時刻のリセットは_後ろへずれた時刻で数える() {
        // ニューヨークは 2026-03-08 の 2:00 から 3:00 に飛ぶ。2:30 は存在しない
        val nyZone = ZoneId.of("America/New_York")
        val spec = ResetSpec(ResetType.DAILY, LocalTime.of(2, 30))
        fun ny(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 3, day, hour, minute).atZone(nyZone).toInstant()

        assertEquals("2026-03-07", TaskReset.periodKey(spec, ny(8, 3, 15), nyZone))
        assertEquals("2026-03-08", TaskReset.periodKey(spec, ny(8, 3, 30), nyZone))
        // 次の日は普通に 2:30
        assertEquals(ny(9, 2, 30), TaskReset.nextResetAt(spec, ny(8, 12, 0), nyZone))
    }

    @Test
    fun 夏時間の終わりで1日が25時間でも_期間は1日ずつ進む() {
        // ニューヨークは 2026-11-01 の 2:00 に 1:00 へ戻る
        val nyZone = ZoneId.of("America/New_York")
        fun ny(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).atZone(nyZone).toInstant()
        assertEquals("2026-10-31", TaskReset.periodKey(daily5, ny(1, 4), nyZone))
        assertEquals("2026-11-01", TaskReset.periodKey(daily5, ny(1, 5), nyZone))
        assertEquals(ny(2, 5), TaskReset.nextResetAt(daily5, ny(1, 5), nyZone))
    }

    @Test
    fun リセットの決まりは文字列に直して戻せる() {
        val weekly = ResetSpec(ResetType.WEEKLY, LocalTime.of(4, 0), DayOfWeek.THURSDAY)
        assertEquals("4 04:00", weekly.encode())
        assertEquals(weekly, ResetSpec.parse(ResetType.WEEKLY, "4 04:00"))
        assertEquals("05:00", daily5.encode())
        assertEquals(daily5, ResetSpec.parse(ResetType.DAILY, "05:00"))
    }

    @Test
    fun 読めない文字列は_5時_月曜として扱う() {
        assertEquals(ResetSpec(ResetType.DAILY, LocalTime.of(5, 0)), ResetSpec.parse(ResetType.DAILY, "こわれた値"))
        assertEquals(
            ResetSpec(ResetType.WEEKLY, LocalTime.of(5, 0), DayOfWeek.MONDAY),
            ResetSpec.parse(ResetType.WEEKLY, "9 25:99"),
        )
        // 曜日の無い毎週は月曜
        assertEquals(DayOfWeek.MONDAY, ResetSpec.parse(ResetType.WEEKLY, "06:30").dayOfWeek)
    }
}
