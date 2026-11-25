package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.Roster
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class YearlyReview(
    val year: Int,
    /** その年がまだ終わっていない。画面では「集計期間(進行中)」と出す。 */
    val inProgress: Boolean,
    val totalSpend: Long,
    val spendCount: Int,
    val topSpendTitle: TitleTotal?,
    /** いちばん課金が多かった月。課金が無ければ null。 */
    val peakMonth: MonthTotal?,
    /** タスクのチェックの数がいちばん多かったタイトルと、その数。 */
    val mostPlayed: Pair<GameTitle, Int>?,
    val tasksChecked: Int,
    val eventsCompleted: Int,
)

object YearlyReviews {

    fun of(roster: Roster, year: Int, now: Instant, zone: ZoneId): YearlyReview {
        val logs = roster.gachaLogs.filter { it.date.year == year }
        val months = Spending.monthly(
            roster.copy(gachaLogs = logs),
            endMonth = java.time.YearMonth.of(year, 12),
            months = 12,
        )

        // チェックした時刻が無い行(取り込んだ古い記録)は、期間の名前の年で数える
        val checks = roster.checkIns.filter { c ->
            c.isChecked && (c.checkedAt?.atZone(zone)?.year ?: yearOfKey(c.periodKey)) == year
        }
        val taskById = roster.tasks.associateBy { it.id }
        val playedByTitle = checks
            .mapNotNull { c -> taskById[c.dailyTaskId]?.let { roster.titleOfAccount(it.gameAccountId) } }
            .groupingBy { it }
            .eachCount()

        return YearlyReview(
            year = year,
            inProgress = now.atZone(zone).year <= year,
            totalSpend = logs.sumOf { it.amount.toLong() },
            spendCount = logs.size,
            topSpendTitle = Spending.byTitle(roster, logs).firstOrNull(),
            peakMonth = months.filter { it.total > 0 }.maxByOrNull { it.total },
            // 同じ数なら、先に登録したタイトル(結果が毎回変わらないように)
            mostPlayed = playedByTitle
                .maxWithOrNull(compareBy<Map.Entry<GameTitle, Int>>({ it.value }, { -it.key.id }))
                ?.toPair(),
            tasksChecked = checks.size,
            eventsCompleted = roster.events.count { it.isCompleted && it.endAt.atZone(zone).year == year },
        )
    }

    /** 記録がある年と今年。新しい順。 */
    fun years(roster: Roster, now: Instant, zone: ZoneId): List<Int> {
        val years = sortedSetOf(now.atZone(zone).year)
        roster.gachaLogs.forEach { years += it.date.year }
        roster.checkIns.forEach { c -> if (c.isChecked) c.checkedAt?.let { years += it.atZone(zone).year } }
        roster.events.forEach { if (it.isCompleted) years += it.endAt.atZone(zone).year }
        return years.toList().reversed()
    }

    private fun yearOfKey(periodKey: String): Int? = runCatching { LocalDate.parse(periodKey).year }.getOrNull()
}
