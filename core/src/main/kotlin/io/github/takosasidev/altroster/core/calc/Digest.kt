package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.Roster
import io.github.takosasidev.altroster.core.model.StaminaResource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class DigestStamina(val resource: StaminaResource, val fullAt: Instant, val isFull: Boolean)

/** 今すぐ気にすること。ホームのいちばん上のカードと、1 日 1 回の通知の両方で使う。 */
data class Digest(
    /** 満タン、または期間内に満タンになるスタミナ。早い順。 */
    val staminas: List<DigestStamina>,
    /** 期間内に終わる、対応済みでないイベント。早い順。 */
    val events: List<GameEvent>,
    /** 今の期間でまだチェックしていないタスク。 */
    val uncheckedTasks: List<DailyTask>,
) {
    val isEmpty: Boolean get() = staminas.isEmpty() && events.isEmpty() && uncheckedTasks.isEmpty()
}

object DigestBuilder {
    /** 通知に 1 つの区分で名前を出す件数。これを超えた分は「ほか N 件」にまとめる。 */
    const val MAX_NAMES = 3

    /**
     * アーカイブしたタイトル・アカウントの物は入れない。
     * [staminaWithin] はホームでは短く(満タン間近)、通知では 24 時間(今日の満タンの予定)にする。
     */
    fun build(
        roster: Roster,
        now: Instant,
        zone: ZoneId,
        staminaWithin: Duration = Duration.ofHours(24),
        eventWithin: Duration = Duration.ofHours(24),
    ): Digest {
        val staminas = roster.activeStaminas.mapNotNull { r ->
            val fullAt = Stamina.fullAt(r) ?: return@mapNotNull null
            if (fullAt.isAfter(now.plus(staminaWithin))) return@mapNotNull null
            DigestStamina(r, fullAt, !fullAt.isAfter(now))
        }.sortedBy { it.fullAt }

        val events = roster.activeEvents
            .filter { !it.isCompleted && it.endAt.isAfter(now) && !it.endAt.isAfter(now.plus(eventWithin)) }
            .sortedBy { it.endAt }

        val checked = TaskReset.checkedTaskIds(roster, now, zone)
        val unchecked = roster.activeTasks.filter { it.id !in checked }

        return Digest(staminas, events, unchecked)
    }

    /** 通知の本文。区分ごとに 1 行。何も無ければ空のリスト(呼ぶ側は通知を出さない)。 */
    fun lines(digest: Digest, roster: Roster, now: Instant, zone: ZoneId): List<String> = buildList {
        if (digest.staminas.isNotEmpty()) {
            val names = digest.staminas.take(MAX_NAMES).joinToString("、") { s ->
                val name = "${roster.accountDisplayName(s.resource.gameAccountId)} ${s.resource.resourceName}"
                if (s.isFull) "$name 満タン" else "$name ${Format.clock(s.fullAt, now, zone)}に満タン"
            }
            add("スタミナ: $names${more(digest.staminas.size)}")
        }
        if (digest.events.isNotEmpty()) {
            val names = digest.events.take(MAX_NAMES).joinToString("、") { e ->
                val title = roster.titleById[e.gameTitleId]?.name.orEmpty()
                "${e.name}($title)あと${Format.duration(EventTime.remaining(e, now))}"
            }
            add("期限: $names${more(digest.events.size)}")
        }
        if (digest.uncheckedTasks.isNotEmpty()) {
            add("未消化のタスク: ${digest.uncheckedTasks.size}件")
        }
    }

    private fun more(size: Int): String = if (size > MAX_NAMES) "、ほか${size - MAX_NAMES}件" else ""
}
