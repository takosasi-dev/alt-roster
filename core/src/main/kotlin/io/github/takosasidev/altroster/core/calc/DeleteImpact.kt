package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.model.Roster

/** 消すと一緒に失われる記録の数。確認の画面に具体的な件数で出す。 */
data class DeleteImpact(
    val accounts: Int = 0,
    val staminas: Int = 0,
    val tasks: Int = 0,
    val checkIns: Int = 0,
    val gachaLogs: Int = 0,
    val events: Int = 0,
) {
    val total: Int get() = accounts + staminas + tasks + checkIns + gachaLogs + events

    /** 「アカウント 2 件、課金の記録 12 件」。0 件の物は書かない。何も無ければ空の文字列。 */
    fun describe(): String = listOf(
        "アカウント" to accounts,
        "スタミナ" to staminas,
        "タスク" to tasks,
        "タスクのチェックの記録" to checkIns,
        "課金の記録" to gachaLogs,
        "イベント" to events,
    ).filter { it.second > 0 }.joinToString("、") { "${it.first} ${it.second} 件" }
}

object DeleteImpacts {

    fun forTitle(roster: Roster, titleId: Long): DeleteImpact {
        val accountIds = roster.accounts.filter { it.gameTitleId == titleId }.map { it.id }.toSet()
        return forAccounts(roster, accountIds).copy(
            accounts = accountIds.size,
            events = roster.events.count { it.gameTitleId == titleId },
        )
    }

    fun forAccount(roster: Roster, accountId: Long): DeleteImpact = forAccounts(roster, setOf(accountId))

    fun forTask(roster: Roster, taskId: Long): DeleteImpact =
        DeleteImpact(checkIns = roster.checkIns.count { it.dailyTaskId == taskId })

    private fun forAccounts(roster: Roster, accountIds: Set<Long>): DeleteImpact {
        val taskIds = roster.tasks.filter { it.gameAccountId in accountIds }.map { it.id }.toSet()
        return DeleteImpact(
            staminas = roster.staminas.count { it.gameAccountId in accountIds },
            tasks = taskIds.size,
            checkIns = roster.checkIns.count { it.dailyTaskId in taskIds },
            gachaLogs = roster.gachaLogs.count { it.gameAccountId in accountIds },
        )
    }
}
