package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.calc.Notice
import io.github.takosasidev.altroster.core.calc.NoticeMessage
import io.github.takosasidev.altroster.core.calc.NoticeText
import io.github.takosasidev.altroster.core.model.Roster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoticeTextTest {
    private val roster = Roster(
        titles = listOf(title(1, "タイトルA")),
        accounts = listOf(account(1, 1)),
        staminas = listOf(stamina(1, 1, max = 100, interval = 5, value = 50, at = jst(2026, 8, 25, 14, 0), threshold = 90)),
        events = listOf(event(1, 1, endAt = jst(2026, 8, 27, 12, 0), name = "イベントX")),
        tasks = listOf(daily(1, 1), daily(2, 1)),
    )

    @Test
    fun 満タンの通知() {
        val at = jst(2026, 8, 25, 18, 10)
        assertEquals(
            NoticeMessage("スタミナが満タンになりました", "タイトルA AP(100/100)"),
            NoticeText.of(Notice.StaminaFull(at, 1), roster, at, JST),
        )
    }

    @Test
    fun 割合の通知には_今の値と満タンの時刻を入れる() {
        val at = jst(2026, 8, 25, 17, 20)
        assertEquals(
            NoticeMessage("スタミナが90%に届きました", "タイトルA AP(90/100)、満タンは 18:10"),
            NoticeText.of(Notice.StaminaThreshold(at, 1, 90), roster, at, JST),
        )
    }

    @Test
    fun 要件定義書の例_イベントの通知には残り時間を入れる() {
        val at = jst(2026, 8, 26, 12, 0)
        assertEquals(
            NoticeMessage("「イベントX」がもうすぐ終了します", "タイトルA あと1日(2026/8/27 12:00 まで)"),
            NoticeText.of(Notice.EventReminder(at, 1), roster, at, JST),
        )
    }

    @Test
    fun リセットの通知は件数とタイトル() {
        val at = jst(2026, 8, 26, 5, 0)
        assertEquals(
            NoticeMessage("タスクがリセットされました", "2件(タイトルA)"),
            NoticeText.of(Notice.TasksReset(at, listOf(1, 2)), roster, at, JST),
        )
    }

    @Test
    fun 要件定義書の例_まとめの通知は_知らせる事が無ければ出さない() {
        val at = jst(2026, 8, 26, 9, 0)
        assertNull(NoticeText.of(Notice.DigestTime(at), Roster(), at, JST))

        // 13:00 の時点: スタミナは満タン、イベントは残り 23 時間、タスクは 2 件が未消化
        val later = jst(2026, 8, 26, 13, 0)
        val message = NoticeText.of(Notice.DigestTime(later), roster, later, JST)
        assertEquals(NoticeText.DIGEST_TITLE, message?.title)
        // スタミナ(満タン)・期限・未消化のタスクの 3 行が、1 件の通知に入る
        assertEquals(3, message?.body?.lines()?.size)
    }

    @Test
    fun 相手が消えていたら出さない() {
        val at = jst(2026, 8, 26, 9, 0)
        assertNull(NoticeText.of(Notice.StaminaFull(at, 99), roster, at, JST))
        assertNull(NoticeText.of(Notice.EventReminder(at, 99), roster, at, JST))
        assertNull(NoticeText.of(Notice.TasksReset(at, listOf(99)), roster, at, JST))
    }
}
