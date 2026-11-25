package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.model.Roster
import java.time.Instant
import java.time.ZoneId

/** 通知に出す文。 */
data class NoticeMessage(val title: String, val body: String)

/**
 * 通知の文の組み立て。
 * 出す直前の記録から作る。相手(スタミナ・イベント・タスク)がもう消えていれば null(出さない)。
 */
object NoticeText {
    const val DIGEST_TITLE = "本日のダイジェスト"

    fun of(notice: Notice, roster: Roster, now: Instant, zone: ZoneId): NoticeMessage? = when (notice) {
        is Notice.StaminaFull -> roster.staminas.find { it.id == notice.resourceId }?.let { r ->
            NoticeMessage(
                "スタミナが満タンになりました",
                "${roster.accountDisplayName(r.gameAccountId)} ${r.resourceName}(${r.maxValue}/${r.maxValue})",
            )
        }

        is Notice.StaminaThreshold -> roster.staminas.find { it.id == notice.resourceId }?.let { r ->
            val full = Stamina.fullAt(r)?.let { "、満タンは ${Format.clock(it, now, zone)}" }.orEmpty()
            NoticeMessage(
                "スタミナが${notice.percent}%に届きました",
                "${roster.accountDisplayName(r.gameAccountId)} ${r.resourceName}" +
                    "(${Stamina.currentValue(r, now)}/${r.maxValue})$full",
            )
        }

        is Notice.EventReminder -> roster.events.find { it.id == notice.eventId }?.let { e ->
            val title = roster.titleById[e.gameTitleId]?.name.orEmpty()
            NoticeMessage(
                "「${e.name}」がもうすぐ終了します",
                "$title あと${Format.duration(EventTime.remaining(e, now))}(${Format.dateTime(e.endAt, zone)} まで)",
            )
        }

        is Notice.TasksReset -> {
            val tasks = roster.tasks.filter { it.id in notice.taskIds }
            if (tasks.isEmpty()) {
                null
            } else {
                val names = tasks.map { roster.accountDisplayName(it.gameAccountId) }.distinct()
                val shown = names.take(DigestBuilder.MAX_NAMES).joinToString("、")
                val more = if (names.size > DigestBuilder.MAX_NAMES) " ほか" else ""
                NoticeMessage("タスクがリセットされました", "${tasks.size}件($shown$more)")
            }
        }

        is Notice.DigestTime -> {
            val lines = DigestBuilder.lines(DigestBuilder.build(roster, now, zone), roster, now, zone)
            if (lines.isEmpty()) null else NoticeMessage(DIGEST_TITLE, lines.joinToString("\n"))
        }
    }
}
