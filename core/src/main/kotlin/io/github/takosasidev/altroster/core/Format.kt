package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.model.AppSettings
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 画面・通知・ウィジェットで同じ書き方にするための文字列の組み立て。 */
object Format {

    /** 残り時間。「3日2時間」「2時間5分」「5分」「1分未満」。負の値は 0 として扱う。 */
    fun duration(d: Duration): String {
        val minutes = d.toMinutes().coerceAtLeast(0)
        val days = minutes / (24 * 60)
        val hours = minutes / 60 % 24
        val mins = minutes % 60
        return when {
            days > 0 -> if (hours > 0) "${days}日${hours}時間" else "${days}日"
            hours > 0 -> if (mins > 0) "${hours}時間${mins}分" else "${hours}時間"
            mins > 0 -> "${mins}分"
            else -> "1分未満"
        }
    }

    /** 金額。「¥12,000」。 */
    fun yen(amount: Long): String {
        val digits = kotlin.math.abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
        return (if (amount < 0) "-¥" else "¥") + digits
    }

    /** 時刻。[now] と同じ日なら「14:30」、翌日なら「明日 14:30」、ほかは「8/26 14:30」。 */
    fun clock(at: Instant, now: Instant, zone: ZoneId): String {
        val t = at.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val hhmm = AppSettings.formatTime(t.hour, t.minute)
        return when (t.toLocalDate()) {
            today -> hhmm
            today.plusDays(1) -> "明日 $hhmm"
            else -> "${t.monthValue}/${t.dayOfMonth} $hhmm"
        }
    }

    /** 日時。「2026/8/26 14:30」。 */
    fun dateTime(at: Instant, zone: ZoneId): String {
        val t = at.atZone(zone)
        return "${t.year}/${t.monthValue}/${t.dayOfMonth} ${AppSettings.formatTime(t.hour, t.minute)}"
    }
}
