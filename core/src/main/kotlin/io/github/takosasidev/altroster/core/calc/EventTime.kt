package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.model.GameEvent
import java.time.Duration
import java.time.Instant

/** カウントダウンの色分け。通常 → 注意(3 日以内) → 緊急(24 時間以内) → 終了。 */
enum class Urgency { NORMAL, WARNING, URGENT, ENDED }

object EventTime {
    val URGENT_WITHIN: Duration = Duration.ofHours(24)
    val WARNING_WITHIN: Duration = Duration.ofHours(72)

    fun remaining(event: GameEvent, now: Instant): Duration = Duration.between(now, event.endAt)

    fun urgency(event: GameEvent, now: Instant): Urgency {
        val left = remaining(event, now)
        return when {
            left.isZero || left.isNegative -> Urgency.ENDED
            left <= URGENT_WITHIN -> Urgency.URGENT
            left <= WARNING_WITHIN -> Urgency.WARNING
            else -> Urgency.NORMAL
        }
    }

    /** 終了前の通知を出す時刻。 */
    fun reminderAt(event: GameEvent): Instant =
        event.endAt.minus(Duration.ofHours(event.reminderBeforeHours.coerceAtLeast(0).toLong()))
}
