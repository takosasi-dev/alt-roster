package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.model.StaminaResource
import java.time.Duration
import java.time.Instant

/**
 * スタミナの回復の計算。記録した値に、経過した時間を回復の間隔で割った分(切り捨て)を足す。
 * 端数(次の 1 回復までの途中の時間)は記録の時刻に残っているので、切り捨てても失われない。
 */
object Stamina {

    /** 記録の値を 0〜最大の中に収める。最大を超える値(あふれ)は扱わず、最大として見る。 */
    private fun base(r: StaminaResource): Int = r.recordedValue.coerceIn(0, r.maxValue.coerceAtLeast(0))

    fun currentValue(r: StaminaResource, now: Instant): Int {
        val max = r.maxValue.coerceAtLeast(0)
        val base = base(r)
        if (r.recoveryIntervalMinutes <= 0) return base
        val elapsed = Duration.between(r.recordedAt, now)
        // 記録の時刻が未来(時計を戻した、取り込んだ)なら、経過は 0 として扱う
        if (elapsed.isNegative) return base
        val gained = elapsed.seconds / (r.recoveryIntervalMinutes * 60L)
        return minOf(max.toLong(), base + gained).toInt()
    }

    /** [target] に届く時刻。記録の時点で届いていれば記録の時刻。回復しない設定なら null。 */
    fun reachAt(r: StaminaResource, target: Int): Instant? {
        val need = target.coerceAtMost(r.maxValue) - base(r)
        if (need <= 0) return r.recordedAt
        if (r.recoveryIntervalMinutes <= 0) return null
        // 最大値と間隔に 9 桁ずつ入れると、分を秒に直す所や時刻の足し算があふれて落ちる。
        // 1000 年より先は「当分届かない」として 1000 年後にそろえる(Int × Int は Long に収まる)
        val minutes = (need.toLong() * r.recoveryIntervalMinutes).coerceAtMost(MAX_REACH_MINUTES)
        return r.recordedAt.plus(Duration.ofMinutes(minutes))
    }

    /** [reachAt] が返す時刻の、記録の時刻からの上限(1000 年)。 */
    private const val MAX_REACH_MINUTES = 1000L * 366 * 24 * 60

    /** 満タンになる時刻。 */
    fun fullAt(r: StaminaResource): Instant? = reachAt(r, r.maxValue)

    fun isFull(r: StaminaResource, now: Instant): Boolean = currentValue(r, now) >= r.maxValue

    /** 0.0〜1.0。ゲージの長さ。 */
    fun fraction(r: StaminaResource, now: Instant): Float =
        if (r.maxValue <= 0) 1f else currentValue(r, now).toFloat() / r.maxValue

    /** 割合の通知で知らせる値(切り上げ)。例: 最大 160 の 90% は 144。 */
    fun thresholdValue(maxValue: Int, percent: Int): Int =
        ((maxValue.toLong() * percent + 99) / 100).toInt()

    /**
     * 割合の通知を出す時刻。設定が無い、1〜99 の外、記録の時点でもう超えている、のどれかなら null
     * (もう超えているのに「◯% に届きました」と出すと嘘になる)。
     */
    fun thresholdAt(r: StaminaResource): Instant? {
        val percent = r.notifyThresholdPercent ?: return null
        if (percent !in 1..99) return null
        val target = thresholdValue(r.maxValue, percent)
        if (target >= r.maxValue || base(r) >= target) return null
        return reachAt(r, target)
    }

    /** 満タンの通知を出す時刻。記録の時点で満タンなら出さない。 */
    fun fullNoticeAt(r: StaminaResource): Instant? {
        if (!r.notifyOnFull || base(r) >= r.maxValue) return null
        return fullAt(r)
    }

    /**
     * 編集の保存。
     *
     * 最大・間隔・値のどれも変えていなければ、記録の値と時刻をそのまま残す
     * (名前だけ直した時に、次の 1 回復までの途中の時間を捨てないため)。
     * どれかを変えたら、今の時刻を新しい基準にして、入力された値から数え直す。
     */
    fun edited(
        old: StaminaResource,
        resourceName: String,
        maxValue: Int,
        recoveryIntervalMinutes: Int,
        enteredValue: Int,
        notifyOnFull: Boolean,
        notifyThresholdPercent: Int?,
        now: Instant,
    ): StaminaResource {
        val untouched = maxValue == old.maxValue &&
            recoveryIntervalMinutes == old.recoveryIntervalMinutes &&
            enteredValue == currentValue(old, now)
        return old.copy(
            resourceName = resourceName,
            maxValue = maxValue,
            recoveryIntervalMinutes = recoveryIntervalMinutes,
            recordedValue = if (untouched) old.recordedValue else enteredValue.coerceIn(0, maxValue),
            recordedAt = if (untouched) old.recordedAt else now,
            notifyOnFull = notifyOnFull,
            notifyThresholdPercent = notifyThresholdPercent,
        )
    }
}
