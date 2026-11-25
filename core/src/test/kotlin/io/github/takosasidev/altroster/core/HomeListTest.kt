package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.calc.DeleteImpacts
import io.github.takosasidev.altroster.core.calc.EventTime
import io.github.takosasidev.altroster.core.calc.HomeList
import io.github.takosasidev.altroster.core.calc.HomeSort
import io.github.takosasidev.altroster.core.calc.Urgency
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.Roster
import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeListTest {
    private val now = jst(2026, 8, 25, 14, 0)

    private val roster = Roster(
        titles = listOf(
            title(1, "Alpha Quest", tags = listOf("RPG", "毎日")),
            title(2, "ベータ戦記", tags = listOf("RPG")),
            title(3, "ガンマ", tags = listOf("パズル")),
            title(4, "引退した物", archived = true, tags = listOf("RPG")),
        ),
        accounts = listOf(account(1, 1), account(2, 2), account(3, 3), account(4, 4)),
        staminas = listOf(
            stamina(1, 1, value = 10, at = now),
            stamina(2, 2, value = 90, at = now),
            // タイトル 3 はスタミナ無し
            stamina(4, 4, value = 100, at = now),
        ),
        events = listOf(
            event(1, 1, endAt = jst(2026, 9, 10)),
            event(2, 3, endAt = jst(2026, 8, 26)),
            event(3, 2, endAt = jst(2026, 8, 25, 15, 0), completed = true),
            event(4, 2, endAt = jst(2026, 8, 20)),
        ),
    )

    private fun ids(query: String = "", tag: String? = null, sort: HomeSort = HomeSort.MANUAL) =
        HomeList.apply(roster, query, tag, sort, now).map { it.id }

    @Test
    fun 既定は登録順_アーカイブしたタイトルは出さない() {
        assertEquals(listOf(1L, 2L, 3L), ids())
    }

    @Test
    fun 名前の検索は_大文字小文字と全角半角を区別しない() {
        assertEquals(listOf(1L), ids(query = "alpha"))
        assertEquals(listOf(1L), ids(query = "ＱＵＥＳＴ"))
        assertEquals(listOf(2L), ids(query = " 戦記 "))
        assertEquals(emptyList<Long>(), ids(query = "無い名前"))
    }

    @Test
    fun タグで絞り込む() {
        assertEquals(listOf(1L, 2L), ids(tag = "RPG"))
        assertEquals(listOf(3L), ids(tag = "パズル"))
        assertEquals(listOf("RPG", "毎日", "パズル"), HomeList.allTags(roster))
    }

    @Test
    fun スタミナの順は_満タンが早いタイトルが上_スタミナの無いタイトルは最後() {
        assertEquals(listOf(2L, 1L, 3L), ids(sort = HomeSort.STAMINA))
    }

    @Test
    fun イベントの順は_終わっていない未対応の期限が早いタイトルが上() {
        // タイトル 2 のイベントは対応済みと終了済みだけなので、期限なしとして最後
        assertEquals(listOf(3L, 1L, 2L), ids(sort = HomeSort.EVENT))
    }

    @Test
    fun タグはカンマと空白を落として重複を除く() {
        assertEquals(listOf("RPG", "a b", "毎日"), GameTitle.normalizeTags(listOf(" RPG ", "a,b", "", "RPG", "毎日")))
    }
}

class DeleteImpactTest {
    private val roster = Roster(
        titles = listOf(title(1), title(2)),
        accounts = listOf(account(1, 1), account(2, 1, label = "サブ"), account(3, 2)),
        staminas = listOf(stamina(1, 1), stamina(2, 1), stamina(3, 2), stamina(4, 3)),
        events = listOf(event(1, 1, endAt = jst(2026, 9, 1)), event(2, 2, endAt = jst(2026, 9, 1))),
        tasks = listOf(daily(1, 1), daily(2, 2), daily(3, 3)),
        checkIns = listOf(checkIn(1, 1, "2026-08-01"), checkIn(2, 1, "2026-08-02"), checkIn(3, 2, "2026-08-01"), checkIn(4, 3, "2026-08-01")),
        gachaLogs = listOf(gacha(1, 1, LocalDate.of(2026, 8, 1), 100), gacha(2, 3, LocalDate.of(2026, 8, 1), 100)),
    )

    @Test
    fun タイトルを消すと失われる件数() {
        val impact = DeleteImpacts.forTitle(roster, 1)
        assertEquals(2, impact.accounts)
        assertEquals(3, impact.staminas)
        assertEquals(2, impact.tasks)
        assertEquals(3, impact.checkIns)
        assertEquals(1, impact.gachaLogs)
        assertEquals(1, impact.events)
        assertEquals(12, impact.total)
        assertEquals(
            "アカウント 2 件、スタミナ 3 件、タスク 2 件、タスクのチェックの記録 3 件、課金の記録 1 件、イベント 1 件",
            impact.describe(),
        )
    }

    @Test
    fun アカウントを消すと失われる件数_0件の物は書かない() {
        val impact = DeleteImpacts.forAccount(roster, 2)
        assertEquals(3, impact.total)
        assertEquals("スタミナ 1 件、タスク 1 件、タスクのチェックの記録 1 件", impact.describe())
    }

    @Test
    fun タスクを消すと失われるのはチェックの記録() {
        assertEquals(2, DeleteImpacts.forTask(roster, 1).total)
        assertEquals("", DeleteImpacts.forTask(roster, 99).describe())
    }
}

class FormatTest {
    private val now = jst(2026, 8, 25, 14, 0)

    @Test
    fun 残り時間() {
        assertEquals("3日2時間", Format.duration(Duration.ofHours(74).plusMinutes(30)))
        assertEquals("2日", Format.duration(Duration.ofDays(2)))
        assertEquals("2時間5分", Format.duration(Duration.ofMinutes(125)))
        assertEquals("1日", Format.duration(Duration.ofHours(24)))
        assertEquals("23時間", Format.duration(Duration.ofHours(23)))
        assertEquals("5分", Format.duration(Duration.ofSeconds(359)))
        assertEquals("1分未満", Format.duration(Duration.ofSeconds(59)))
        assertEquals("1分未満", Format.duration(Duration.ofMinutes(-10)))
    }

    @Test
    fun 金額は3桁ごとに区切る() {
        assertEquals("¥0", Format.yen(0))
        assertEquals("¥999", Format.yen(999))
        assertEquals("¥12,000", Format.yen(12000))
        assertEquals("¥1,234,567", Format.yen(1234567))
    }

    @Test
    fun 時刻は_今日なら時刻だけ_明日は明日_ほかは日付も() {
        assertEquals("18:10", Format.clock(jst(2026, 8, 25, 18, 10), now, JST))
        assertEquals("明日 05:00", Format.clock(jst(2026, 8, 26, 5, 0), now, JST))
        assertEquals("8/31 05:00", Format.clock(jst(2026, 8, 31, 5, 0), now, JST))
        assertEquals("2026/8/31 05:00", Format.dateTime(jst(2026, 8, 31, 5, 0), JST))
    }

    @Test
    fun カウントダウンの色分け() {
        val end = jst(2026, 8, 28, 14, 0)
        assertEquals(Urgency.NORMAL, EventTime.urgency(event(1, 1, endAt = end), jst(2026, 8, 25, 13, 59)))
        assertEquals(Urgency.WARNING, EventTime.urgency(event(1, 1, endAt = end), now))
        assertEquals(Urgency.URGENT, EventTime.urgency(event(1, 1, endAt = end), jst(2026, 8, 27, 14, 0)))
        assertEquals(Urgency.ENDED, EventTime.urgency(event(1, 1, endAt = end), end))
        assertEquals(jst(2026, 8, 27, 14, 0), EventTime.reminderAt(event(1, 1, endAt = end, hours = 24)))
    }
}
