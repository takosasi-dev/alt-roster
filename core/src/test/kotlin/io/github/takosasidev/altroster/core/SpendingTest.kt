package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.calc.Insights
import io.github.takosasidev.altroster.core.calc.Spending
import io.github.takosasidev.altroster.core.calc.YearlyReviews
import io.github.takosasidev.altroster.core.model.Roster
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpendingTest {
    private val base = Roster(
        titles = listOf(title(1, "タイトルA"), title(2, "タイトルB"), title(3, "タイトルC", archived = true)),
        accounts = listOf(account(1, 1), account(2, 2), account(3, 3), account(4, 1, label = "サブ")),
    )
    private val aug = YearMonth.of(2026, 8)
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test
    fun 要件定義書の例_月内の5件で合計12000円() {
        val roster = base.copy(
            gachaLogs = listOf(
                gacha(1, 1, d(8, 1), 3000), gacha(2, 1, d(8, 5), 1000), gacha(3, 2, d(8, 10), 5000),
                gacha(4, 4, d(8, 20), 2000), gacha(5, 2, d(8, 31), 1000),
                // 前後の月の記録は数えない
                gacha(6, 1, d(7, 31), 9999), gacha(7, 1, d(9, 1), 9999),
            ),
        )
        val total = Spending.monthTotal(roster, aug)
        assertEquals(12000L, total.total)
        assertEquals(5, total.count)
    }

    @Test
    fun 月別は_記録の無い月も0で入れる() {
        val roster = base.copy(gachaLogs = listOf(gacha(1, 1, d(6, 15), 1200), gacha(2, 1, d(8, 1), 300)))
        val months = Spending.monthly(roster, aug, months = 4)
        assertEquals(listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7), aug), months.map { it.month })
        assertEquals(listOf(0L, 1200L, 0L, 300L), months.map { it.total })
    }

    @Test
    fun タイトル別は多い順_同じタイトルのアカウントは足す_アーカイブしたタイトルも数える() {
        val roster = base.copy(
            gachaLogs = listOf(
                gacha(1, 1, d(8, 1), 1000), gacha(2, 4, d(8, 2), 1500),
                gacha(3, 2, d(8, 3), 2000), gacha(4, 3, d(8, 4), 9000), gacha(5, 2, d(7, 1), 50000),
            ),
        )
        val byTitle = Spending.byTitle(roster, aug)
        assertEquals(listOf("タイトルC" to 9000L, "タイトルA" to 2500L, "タイトルB" to 2000L), byTitle.map { it.title.name to it.total })
        assertEquals("タイトルB", Spending.byTitle(roster).first().title.name)
    }

    // ---- 気づき --------------------------------------------------------------

    private val now = jst(2026, 8, 25, 12, 0)

    @Test
    fun 今月いちばん課金したタイトルを伝える() {
        val roster = base.copy(gachaLogs = listOf(gacha(1, 1, d(8, 1), 3000), gacha(2, 2, d(8, 2), 1000)))
        val lines = Insights.generate(roster, now, JST)
        assertEquals(listOf("今月は「タイトルA」への課金が最も多いです(¥3,000、全体の75%)。"), lines)
    }

    @Test
    fun 先月より2割以上増えたら伝える_少しの差では伝えない() {
        val up = base.copy(gachaLogs = listOf(gacha(1, 1, d(7, 1), 1000), gacha(2, 1, d(8, 1), 1500)))
        assertTrue(Insights.generate(up, now, JST).any { it.contains("先月より50%多く") })

        val down = base.copy(gachaLogs = listOf(gacha(1, 1, d(7, 1), 2000), gacha(2, 1, d(8, 1), 1000)))
        assertTrue(Insights.generate(down, now, JST).any { it.contains("先月より50%少なく") })

        val flat = base.copy(gachaLogs = listOf(gacha(1, 1, d(7, 1), 1000), gacha(2, 1, d(8, 1), 1100)))
        assertFalse(Insights.generate(flat, now, JST).any { it.contains("先月より") })
    }

    @Test
    fun 記録が何も無ければ_気づきも無い() {
        assertTrue(Insights.generate(base, now, JST).isEmpty())
    }

    @Test
    fun デイリーの消化率が低いタイトルを伝える() {
        // タイトルA: デイリー 2 つ × 7 日 = 14 回のうち 2 回 = 14%。タイトルB: 1 つ × 7 日のうち 6 回 = 85%
        val roster = base.copy(
            tasks = listOf(daily(1, 1), daily(2, 1), daily(3, 2), weekly(4, 1)),
            checkIns = listOf(checkIn(1, 1, "2026-08-25"), checkIn(2, 2, "2026-08-24")) +
                (20..25).map { checkIn(10L + it, 3, "2026-08-$it") },
        )
        assertEquals(listOf("タイトルA" to 14), Insights.lowDailyRates(roster, now, JST))
        assertTrue(Insights.generate(roster, now, JST).single().startsWith("「タイトルA」のデイリータスクの消化率が低い"))
    }

    @Test
    fun 登録して間もないアカウントは_消化率を判定しない() {
        val roster = Roster(
            titles = listOf(title(1)),
            accounts = listOf(account(1, 1, createdAt = jst(2026, 8, 24, 20, 0))),
            tasks = listOf(daily(1, 1)),
        )
        assertTrue(Insights.lowDailyRates(roster, now, JST).isEmpty())
    }

    // ---- 年間の振り返り ------------------------------------------------------

    @Test
    fun 年間の振り返り_進行中の年() {
        val roster = base.copy(
            tasks = listOf(daily(1, 1), daily(2, 2)),
            checkIns = listOf(
                checkIn(1, 1, "2026-03-01", at = jst(2026, 3, 1, 10, 0)),
                checkIn(2, 1, "2026-03-02", at = jst(2026, 3, 2, 10, 0)),
                checkIn(3, 2, "2026-03-02", at = jst(2026, 3, 2, 10, 0)),
                checkIn(4, 2, "2025-12-31", at = jst(2025, 12, 31, 10, 0)),
                checkIn(5, 1, "2026-03-03", checked = false),
            ),
            events = listOf(
                event(1, 1, endAt = jst(2026, 2, 1), completed = true),
                event(2, 1, endAt = jst(2026, 2, 2), completed = false),
                event(3, 1, endAt = jst(2025, 2, 1), completed = true),
            ),
            gachaLogs = listOf(
                gacha(1, 1, d(1, 10), 3000), gacha(2, 2, d(3, 5), 8000), gacha(3, 2, d(3, 6), 1000),
                gacha(4, 1, LocalDate.of(2025, 12, 31), 99999),
            ),
        )
        val review = YearlyReviews.of(roster, 2026, now, JST)
        assertTrue(review.inProgress)
        assertEquals(12000L, review.totalSpend)
        assertEquals(3, review.spendCount)
        assertEquals("タイトルB", review.topSpendTitle?.title?.name)
        assertEquals(YearMonth.of(2026, 3), review.peakMonth?.month)
        assertEquals(9000L, review.peakMonth?.total)
        assertEquals("タイトルA" to 2, review.mostPlayed?.let { it.first.name to it.second })
        assertEquals(3, review.tasksChecked)
        assertEquals(1, review.eventsCompleted)
        assertEquals(listOf(2026, 2025), YearlyReviews.years(roster, now, JST))
    }

    @Test
    fun 年間の振り返り_終わった年と_記録の無い年() {
        val past = YearlyReviews.of(base, 2025, now, JST)
        assertFalse(past.inProgress)
        assertEquals(0L, past.totalSpend)
        assertNull(past.topSpendTitle)
        assertNull(past.peakMonth)
        assertNull(past.mostPlayed)
        assertEquals(listOf(2026), YearlyReviews.years(base, now, JST))
    }
}
