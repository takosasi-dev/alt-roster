package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.calc.DigestBuilder
import io.github.takosasidev.altroster.core.model.Roster
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigestTest {
    private val nineAm = jst(2026, 8, 25, 9, 0)

    private val base = Roster(
        titles = listOf(title(1, "タイトルA"), title(2, "タイトルB", archived = true)),
        accounts = listOf(account(1, 1), account(2, 2)),
    )

    @Test
    fun 要件定義書の例_満タンの予定1件と24時間以内のイベント1件を_1つにまとめる() {
        val roster = base.copy(
            // 8:00 に 50 → 12:10 に満タン
            staminas = listOf(stamina(1, 1, value = 50, at = jst(2026, 8, 25, 8, 0))),
            events = listOf(event(1, 1, endAt = jst(2026, 8, 25, 21, 0), name = "イベントX")),
        )
        val digest = DigestBuilder.build(roster, nineAm, JST)
        assertFalse(digest.isEmpty)
        assertEquals(1, digest.staminas.size)
        assertEquals(1, digest.events.size)
        assertEquals(
            listOf("スタミナ: タイトルA AP 12:10に満タン", "期限: イベントX(タイトルA)あと12時間"),
            DigestBuilder.lines(digest, roster, nineAm, JST),
        )
    }

    @Test
    fun 要件定義書の例_知らせる事が何も無ければ空() {
        val digest = DigestBuilder.build(base, nineAm, JST)
        assertTrue(digest.isEmpty)
        assertTrue(DigestBuilder.lines(digest, base, nineAm, JST).isEmpty())
    }

    @Test
    fun もう満タンのスタミナは満タンと書く() {
        val roster = base.copy(staminas = listOf(stamina(1, 1, max = 100, value = 100, at = jst(2026, 8, 24, 8, 0))))
        val digest = DigestBuilder.build(roster, nineAm, JST)
        assertTrue(digest.staminas.single().isFull)
        assertEquals(listOf("スタミナ: タイトルA AP 満タン"), DigestBuilder.lines(digest, roster, nineAm, JST))
    }

    @Test
    fun 期間より先に満タンになるスタミナは入れない_ホームは期間を短くする() {
        // 12:10 に満タン
        val roster = base.copy(staminas = listOf(stamina(1, 1, value = 50, at = jst(2026, 8, 25, 8, 0))))
        assertTrue(DigestBuilder.build(roster, nineAm, JST, staminaWithin = Duration.ofHours(1)).isEmpty)
        assertFalse(DigestBuilder.build(roster, jst(2026, 8, 25, 11, 10), JST, staminaWithin = Duration.ofHours(1)).isEmpty)
    }

    @Test
    fun 対応済み_終了済み_24時間より先のイベントは入れない() {
        val roster = base.copy(
            events = listOf(
                event(1, 1, endAt = jst(2026, 8, 25, 21, 0), completed = true),
                event(2, 1, endAt = jst(2026, 8, 25, 8, 0)),
                event(3, 1, endAt = jst(2026, 8, 26, 9, 1)),
                event(4, 1, endAt = jst(2026, 8, 26, 9, 0)),
            ),
        )
        assertEquals(listOf(4L), DigestBuilder.build(roster, nineAm, JST).events.map { it.id })
    }

    @Test
    fun 未消化のタスクは件数で伝える_チェック済みとアーカイブの物は数えない() {
        val roster = base.copy(
            tasks = listOf(daily(1, 1), daily(2, 1), daily(3, 1), daily(4, 2)),
            checkIns = listOf(checkIn(1, 1, "2026-08-25")),
        )
        val digest = DigestBuilder.build(roster, nineAm, JST)
        assertEquals(listOf(2L, 3L), digest.uncheckedTasks.map { it.id })
        assertEquals(listOf("未消化のタスク: 2件"), DigestBuilder.lines(digest, roster, nineAm, JST))
    }

    @Test
    fun 名前を出すのは3件まで_残りは件数にまとめる() {
        val roster = base.copy(
            staminas = (1L..5L).map { stamina(it, 1, max = 100, value = 100, at = jst(2026, 8, 24, 8, it.toInt()), name = "S$it") },
        )
        val line = DigestBuilder.lines(DigestBuilder.build(roster, nineAm, JST), roster, nineAm, JST).single()
        assertEquals("スタミナ: タイトルA S1 満タン、タイトルA S2 満タン、タイトルA S3 満タン、ほか2件", line)
    }

    @Test
    fun アカウントが2つ以上あるタイトルは_アカウントの名前も出す() {
        val roster = base.copy(
            accounts = listOf(account(1, 1, label = "メイン"), account(3, 1, label = "サブ")),
            staminas = listOf(stamina(1, 3, max = 100, value = 100, at = jst(2026, 8, 24, 8, 0))),
        )
        val line = DigestBuilder.lines(DigestBuilder.build(roster, nineAm, JST), roster, nineAm, JST).single()
        assertEquals("スタミナ: タイトルA(サブ) AP 満タン", line)
    }
}
