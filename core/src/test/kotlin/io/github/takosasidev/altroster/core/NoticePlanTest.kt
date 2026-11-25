package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.calc.Notice
import io.github.takosasidev.altroster.core.calc.NoticePlan
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.Roster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoticePlanTest {
    /** まとめの通知を切った設定。スタミナやイベントだけを確かめるテストで使う。 */
    private val quiet = AppSettings(digestNotificationEnabled = false)

    private val base = Roster(
        titles = listOf(title(1), title(2, archived = true)),
        accounts = listOf(account(1, 1), account(2, 2), account(3, 1, label = "サブ", archived = true)),
    )

    // 14:00 に 50。割合の通知(90%)は 17:20、満タンは 18:10
    private val ap = stamina(1, 1, max = 100, interval = 5, value = 50, at = jst(2026, 8, 25, 14, 0), threshold = 90)

    @Test
    fun 次に鳴らす時刻は_いちばん早い通知の時刻() {
        val roster = base.copy(staminas = listOf(ap))
        assertEquals(jst(2026, 8, 25, 17, 20), NoticePlan.nextTriggerAfter(roster, quiet, jst(2026, 8, 25, 14, 0), JST))
        assertEquals(jst(2026, 8, 25, 18, 10), NoticePlan.nextTriggerAfter(roster, quiet, jst(2026, 8, 25, 17, 20), JST))
        assertNull(NoticePlan.nextTriggerAfter(roster, quiet, jst(2026, 8, 25, 18, 10), JST))
    }

    @Test
    fun 要件定義書の例_満タンになる時刻に通知が出る() {
        val roster = base.copy(staminas = listOf(ap.copy(notifyThresholdPercent = null)))
        val before = NoticePlan.dueBetween(roster, quiet, jst(2026, 8, 25, 14, 0), jst(2026, 8, 25, 18, 9), JST)
        val atFull = NoticePlan.dueBetween(roster, quiet, jst(2026, 8, 25, 14, 0), jst(2026, 8, 25, 18, 10), JST)
        assertTrue(before.isEmpty())
        assertEquals(listOf<Notice>(Notice.StaminaFull(jst(2026, 8, 25, 18, 10), 1)), atFull)
    }

    @Test
    fun 範囲の始まりちょうどの通知は_前の回で出した物として入れない() {
        val roster = base.copy(staminas = listOf(ap))
        val due = NoticePlan.dueBetween(roster, quiet, jst(2026, 8, 25, 17, 20), jst(2026, 8, 25, 18, 9), JST)
        assertTrue(due.isEmpty())
    }

    @Test
    fun 割合の通知は割合の時刻に出る() {
        val roster = base.copy(staminas = listOf(ap))
        val due = NoticePlan.dueBetween(roster, quiet, jst(2026, 8, 25, 14, 0), jst(2026, 8, 25, 17, 20), JST)
        assertEquals(listOf<Notice>(Notice.StaminaThreshold(jst(2026, 8, 25, 17, 20), 1, 90)), due)
    }

    @Test
    fun 長く止まっていて満タンも過ぎていたら_割合の通知は出さず満タンだけ() {
        val roster = base.copy(staminas = listOf(ap))
        val due = NoticePlan.dueBetween(roster, quiet, jst(2026, 8, 25, 14, 0), jst(2026, 8, 26, 9, 0), JST)
        assertEquals(listOf<Notice>(Notice.StaminaFull(jst(2026, 8, 25, 18, 10), 1)), due)
    }

    @Test
    fun アーカイブしたタイトルとアカウントの物は対象にしない() {
        val roster = base.copy(
            staminas = listOf(stamina(1, 2), stamina(2, 3)),
            events = listOf(event(1, 2, endAt = jst(2026, 8, 27, 12, 0))),
            tasks = listOf(daily(1, 2), daily(2, 3)),
        )
        val all = AppSettings(digestNotificationEnabled = false, resetNotificationEnabled = true)
        assertNull(NoticePlan.nextTriggerAfter(roster, all, jst(2026, 8, 25, 14, 0), JST))
        assertTrue(NoticePlan.dueBetween(roster, all, jst(2026, 8, 25, 14, 0), jst(2026, 9, 1, 0, 0), JST).isEmpty())
    }

    @Test
    fun 要件定義書の例_イベントの終了の24時間前に通知が出る() {
        val roster = base.copy(events = listOf(event(7, 1, endAt = jst(2026, 8, 27, 12, 0), hours = 24)))
        assertEquals(jst(2026, 8, 26, 12, 0), NoticePlan.nextTriggerAfter(roster, quiet, jst(2026, 8, 25, 14, 0), JST))
        val due = NoticePlan.dueBetween(roster, quiet, jst(2026, 8, 26, 11, 0), jst(2026, 8, 26, 12, 0), JST)
        assertEquals(listOf<Notice>(Notice.EventReminder(jst(2026, 8, 26, 12, 0), 7)), due)
    }

    @Test
    fun 対応済みのイベントと_もう終わったイベントは知らせない() {
        val done = base.copy(events = listOf(event(1, 1, endAt = jst(2026, 8, 27, 12, 0), completed = true)))
        assertNull(NoticePlan.nextTriggerAfter(done, quiet, jst(2026, 8, 25, 14, 0), JST))
        assertTrue(NoticePlan.dueBetween(done, quiet, jst(2026, 8, 25, 14, 0), jst(2026, 8, 26, 13, 0), JST).isEmpty())

        // 止まっている間に終了の時刻まで過ぎた
        val ended = base.copy(events = listOf(event(1, 1, endAt = jst(2026, 8, 27, 12, 0))))
        assertTrue(NoticePlan.dueBetween(ended, quiet, jst(2026, 8, 25, 14, 0), jst(2026, 8, 28, 0, 0), JST).isEmpty())
    }

    @Test
    fun まとめの通知は毎日の決まった時刻() {
        val settings = AppSettings(digestNotificationEnabled = true, digestNotificationTime = "09:00")
        assertEquals(jst(2026, 8, 26, 9, 0), NoticePlan.nextTriggerAfter(base, settings, jst(2026, 8, 25, 14, 0), JST))
        assertEquals(jst(2026, 8, 25, 9, 0), NoticePlan.nextTriggerAfter(base, settings, jst(2026, 8, 25, 8, 59), JST))
    }

    @Test
    fun 何日も止まっていても_まとめの通知は直近の1回だけ() {
        val settings = AppSettings(digestNotificationEnabled = true, digestNotificationTime = "09:00")
        val due = NoticePlan.dueBetween(base, settings, jst(2026, 8, 25, 8, 0), jst(2026, 8, 28, 10, 0), JST)
        assertEquals(listOf<Notice>(Notice.DigestTime(jst(2026, 8, 28, 9, 0))), due)
    }

    @Test
    fun リセットの通知は既定では出さない() {
        val roster = base.copy(tasks = listOf(daily(1, 1)))
        assertNull(NoticePlan.nextTriggerAfter(roster, quiet, jst(2026, 8, 25, 14, 0), JST))
        assertTrue(NoticePlan.dueBetween(roster, quiet, jst(2026, 8, 25, 4, 0), jst(2026, 8, 25, 6, 0), JST).isEmpty())
    }

    @Test
    fun リセットの通知を入れると_同じ時にリセットされたタスクを1件にまとめる() {
        val settings = AppSettings(digestNotificationEnabled = false, resetNotificationEnabled = true)
        val roster = base.copy(tasks = listOf(daily(1, 1), daily(2, 1), daily(3, 1, time = "12:00"), weekly(4, 1, time = "3 05:00")))
        // 2026-08-25 は火曜。水曜 5 時のウィークリーはまだ
        assertEquals(jst(2026, 8, 26, 5, 0), NoticePlan.nextTriggerAfter(roster, settings, jst(2026, 8, 25, 14, 0), JST))
        val due = NoticePlan.dueBetween(roster, settings, jst(2026, 8, 25, 4, 0), jst(2026, 8, 25, 5, 0), JST)
        assertEquals(listOf<Notice>(Notice.TasksReset(jst(2026, 8, 25, 5, 0), listOf(1, 2))), due)
        // 水曜の 5 時は、デイリー 2 件とウィークリー 1 件
        val wednesday = NoticePlan.dueBetween(roster, settings, jst(2026, 8, 26, 4, 0), jst(2026, 8, 26, 5, 0), JST)
        assertEquals(listOf<Notice>(Notice.TasksReset(jst(2026, 8, 26, 5, 0), listOf(1, 2, 4))), wednesday)
    }

    @Test
    fun 範囲が空なら何も出さない() {
        val roster = base.copy(staminas = listOf(ap))
        val t = jst(2026, 8, 25, 18, 10)
        assertTrue(NoticePlan.dueBetween(roster, quiet, t, t, JST).isEmpty())
        // 時計を戻された(前に処理した時刻が今より後)
        assertTrue(NoticePlan.dueBetween(roster, quiet, t.plusSeconds(60), t, JST).isEmpty())
    }
}
