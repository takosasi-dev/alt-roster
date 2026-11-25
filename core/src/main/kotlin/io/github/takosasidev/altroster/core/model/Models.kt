package io.github.takosasidev.altroster.core.model

import java.time.Instant
import java.time.LocalDate

/** ゲームのタイトル。名前は利用者が打ち込む(初期データは持たない)。 */
data class GameTitle(
    val id: Long = 0L,
    val name: String,
    /** 内蔵のアイコンの名前。null なら頭文字と色で表す。 */
    val iconKey: String? = null,
    val colorArgb: Int,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    companion object {
        /**
         * タグは DB とバックアップでカンマ区切りの 1 列に畳むので、タグ自体からカンマと改行を落とす。
         * 前後の空白を取り、空と重複を除く。
         */
        fun normalizeTags(raw: List<String>): List<String> =
            raw.map { it.replace(",", " ").replace("\n", " ").replace("\r", " ").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
    }
}

/** タイトルごとのアカウント(メイン・サブなど)。認証に使える情報は持たない。 */
data class GameAccount(
    val id: Long = 0L,
    val gameTitleId: Long,
    val label: String,
    /** ログインの手がかりの自由なメモ。パスワードや引き継ぎコードを入れる欄ではない。 */
    val loginHintMemo: String? = null,
    val createdAt: Instant,
    val isArchived: Boolean = false,
)

/** スタミナの種別ごとの設定と、最後に記録した値。 */
data class StaminaResource(
    val id: Long = 0L,
    val gameAccountId: Long,
    val resourceName: String,
    val maxValue: Int,
    val recoveryIntervalMinutes: Int,
    val recordedValue: Int,
    val recordedAt: Instant,
    val notifyOnFull: Boolean = true,
    /** 満タン以外で知らせる割合(1〜99)。null なら知らせない。 */
    val notifyThresholdPercent: Int? = null,
)

data class GameEvent(
    val id: Long = 0L,
    val gameTitleId: Long,
    val name: String,
    val endAt: Instant,
    val reminderBeforeHours: Int = 24,
    val isCompleted: Boolean = false,
)

enum class ResetType { DAILY, WEEKLY }

data class DailyTask(
    val id: Long = 0L,
    val gameAccountId: Long,
    val label: String,
    val resetType: ResetType,
    /** 毎日は "HH:mm"、毎週は "曜日の番号(1=月〜7=日) HH:mm"。読み書きは ResetSpec が行う。 */
    val resetTime: String,
    val sortOrder: Int = 0,
)

data class TaskCheckIn(
    val id: Long = 0L,
    val dailyTaskId: Long,
    /** 対象の期間の始まりの日付(ISO)。毎週のタスクは、その週のリセットの日。 */
    val periodKey: String,
    val isChecked: Boolean,
    val checkedAt: Instant? = null,
)

data class GachaLog(
    val id: Long = 0L,
    val gameAccountId: Long,
    val date: LocalDate,
    /** 金額(円)。 */
    val amount: Int,
    val currencyType: String? = null,
    val memo: String? = null,
    val createdAt: Instant,
)

/**
 * 端末に入っている記録の全部。
 *
 * 個人用で件数が少ない(多くても数千行)ので、画面も通知もウィジェットも、
 * 全部を読んでからメモリの上で計算する。SQL を機能ごとに書き分けない。
 */
data class Roster(
    val titles: List<GameTitle> = emptyList(),
    val accounts: List<GameAccount> = emptyList(),
    val staminas: List<StaminaResource> = emptyList(),
    val events: List<GameEvent> = emptyList(),
    val tasks: List<DailyTask> = emptyList(),
    val checkIns: List<TaskCheckIn> = emptyList(),
    val gachaLogs: List<GachaLog> = emptyList(),
) {
    val titleById: Map<Long, GameTitle> by lazy { titles.associateBy { it.id } }
    val accountById: Map<Long, GameAccount> by lazy { accounts.associateBy { it.id } }

    fun titleOfAccount(accountId: Long): GameTitle? =
        accountById[accountId]?.let { titleById[it.gameTitleId] }

    /** アーカイブしていないタイトル。表示の順。 */
    val activeTitles: List<GameTitle> by lazy {
        titles.filter { !it.isArchived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }

    /** タイトルもアカウントもアーカイブしていないアカウント。 */
    val activeAccounts: List<GameAccount> by lazy {
        accounts.filter { !it.isArchived && titleById[it.gameTitleId]?.isArchived == false }
    }

    private val activeAccountIds: Set<Long> by lazy { activeAccounts.map { it.id }.toSet() }

    val activeStaminas: List<StaminaResource> by lazy { staminas.filter { it.gameAccountId in activeAccountIds } }
    val activeTasks: List<DailyTask> by lazy {
        tasks.filter { it.gameAccountId in activeAccountIds }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }
    val activeEvents: List<GameEvent> by lazy { events.filter { titleById[it.gameTitleId]?.isArchived == false } }

    fun accountsOf(titleId: Long, includeArchived: Boolean = false): List<GameAccount> =
        accounts.filter { it.gameTitleId == titleId && (includeArchived || !it.isArchived) }

    /**
     * 通知や一覧に出す名前。アカウントが 1 つだけのタイトルは、タイトルの名前だけにする
     * (「メイン」を毎回付けても情報が増えないため)。
     */
    fun accountDisplayName(accountId: Long): String {
        val account = accountById[accountId] ?: return ""
        val title = titleById[account.gameTitleId] ?: return account.label
        return if (accountsOf(title.id).size > 1) "${title.name}(${account.label})" else title.name
    }
}
