package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.Roster
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 決まった条件に当てはめて文を作るだけの気づき。生成 AI も通信も使わない。
 * 条件に当たらない物は出さない(毎回同じ文が並ぶと読まれなくなる)。
 */
object Insights {
    /** 先月との差がこの割合以上のときだけ、増減を伝える。 */
    const val CHANGE_PERCENT = 20

    /** デイリーの消化率がこれ未満のタイトルを「低い」とする。 */
    const val LOW_RATE_PERCENT = 40

    /** 消化率を見る日数。 */
    const val RATE_DAYS = 7

    /** 登録してからこの日数に満たないアカウントは、消化率を判定しない。 */
    const val MIN_DAYS = 3

    fun generate(roster: Roster, now: Instant, zone: ZoneId): List<String> = buildList {
        val thisMonth = YearMonth.from(now.atZone(zone))
        val current = Spending.monthTotal(roster, thisMonth)
        val previous = Spending.monthTotal(roster, thisMonth.minusMonths(1))

        if (current.total > 0) {
            val top = Spending.byTitle(roster, thisMonth).first()
            val share = top.total * 100 / current.total
            add("今月は「${top.title.name}」への課金が最も多いです(${Format.yen(top.total)}、全体の$share%)。")
        }

        if (current.total > 0 && previous.total > 0) {
            val change = (current.total - previous.total) * 100 / previous.total
            if (change >= CHANGE_PERCENT) {
                add("今月の課金は先月より$change%多くなっています(先月 ${Format.yen(previous.total)} → 今月 ${Format.yen(current.total)})。")
            } else if (change <= -CHANGE_PERCENT) {
                add("今月の課金は先月より${-change}%少なくなっています(先月 ${Format.yen(previous.total)} → 今月 ${Format.yen(current.total)})。")
            }
        }

        if (current.total == 0L && previous.total > 0) {
            add("今月はまだ課金の記録がありません(先月は ${Format.yen(previous.total)})。")
        }

        lowDailyRates(roster, now, zone).forEach { (name, rate) ->
            add("「$name」のデイリータスクの消化率が低い傾向があります(直近${RATE_DAYS}日で$rate%)。")
        }
    }

    /**
     * タイトルごとの、直近 [RATE_DAYS] 日のデイリーの消化率(%)。[LOW_RATE_PERCENT] 未満の物だけ。
     *
     * 分母は「タスクの数 × 日数」。タスクを作った日は持っていないので、アカウントの登録日からの日数で
     * 頭を打たせる(昨日作ったアカウントを 7 日分の未消化として数えない)。
     */
    fun lowDailyRates(roster: Roster, now: Instant, zone: ZoneId): List<Pair<String, Int>> {
        val today = now.atZone(zone).toLocalDate()
        val since = today.minusDays(RATE_DAYS - 1L).toString()
        val checkedByTask = roster.checkIns
            .filter { it.isChecked && it.periodKey >= since }
            .groupBy { it.dailyTaskId }
            .mapValues { (_, list) -> list.map { it.periodKey }.distinct().size }

        return roster.activeTitles.mapNotNull { title ->
            var expected = 0L
            var done = 0L
            roster.accountsOf(title.id).forEach { account ->
                val age = ChronoUnit.DAYS.between(account.createdAt.atZone(zone).toLocalDate(), today) + 1
                if (age < MIN_DAYS) return@forEach
                val days = minOf(RATE_DAYS.toLong(), age)
                roster.tasks.filter { it.gameAccountId == account.id && it.resetType == ResetType.DAILY }.forEach { task ->
                    expected += days
                    done += minOf(days, (checkedByTask[task.id] ?: 0).toLong())
                }
            }
            if (expected == 0L) return@mapNotNull null
            val rate = (done * 100 / expected).toInt()
            if (rate < LOW_RATE_PERCENT) title.name to rate else null
        }
    }
}
