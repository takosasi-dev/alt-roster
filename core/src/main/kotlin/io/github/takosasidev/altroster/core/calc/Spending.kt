package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.model.GachaLog
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.Roster
import java.time.YearMonth

data class MonthTotal(val month: YearMonth, val total: Long, val count: Int)

data class TitleTotal(val title: GameTitle, val total: Long, val count: Int)

/**
 * 課金の集計。アーカイブしたタイトルの記録も数える
 * (アーカイブは一覧から隠すだけで、使った金額が無かったことにはならない)。
 */
object Spending {

    fun monthTotal(roster: Roster, month: YearMonth): MonthTotal {
        val logs = roster.gachaLogs.filter { YearMonth.from(it.date) == month }
        return MonthTotal(month, logs.sumOf { it.amount.toLong() }, logs.size)
    }

    /** [endMonth] までの [months] か月分。記録の無い月も 0 で入れる(グラフの横軸を詰めない)。 */
    fun monthly(roster: Roster, endMonth: YearMonth, months: Int = 12): List<MonthTotal> {
        val byMonth = roster.gachaLogs.groupBy { YearMonth.from(it.date) }
        return (months - 1 downTo 0).map { back ->
            val month = endMonth.minusMonths(back.toLong())
            val logs = byMonth[month].orEmpty()
            MonthTotal(month, logs.sumOf { it.amount.toLong() }, logs.size)
        }
    }

    /** タイトル別の合計。多い順。[month] が null なら全部の期間。 */
    fun byTitle(roster: Roster, month: YearMonth? = null): List<TitleTotal> =
        byTitle(roster, roster.gachaLogs.filter { month == null || YearMonth.from(it.date) == month })

    fun byTitle(roster: Roster, logs: List<GachaLog>): List<TitleTotal> =
        logs.groupBy { roster.titleOfAccount(it.gameAccountId) }
            .mapNotNull { (title, list) ->
                title?.let { TitleTotal(it, list.sumOf { log -> log.amount.toLong() }, list.size) }
            }
            .sortedWith(compareByDescending<TitleTotal> { it.total }.thenBy { it.title.name })
}
