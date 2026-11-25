package io.github.takosasidev.altroster.core.calc

import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.Roster
import java.text.Normalizer
import java.time.Instant

enum class HomeSort(val label: String) {
    MANUAL("登録順"),
    STAMINA("スタミナが満タンに近い順"),
    EVENT("イベントの期限が近い順"),
}

/** ホームのタイトルの一覧の、検索・絞り込み・並べ替え。アーカイブしたタイトルは出さない。 */
object HomeList {

    fun apply(roster: Roster, query: String, tag: String?, sort: HomeSort, now: Instant): List<GameTitle> {
        val q = fold(query)
        val filtered = roster.activeTitles.filter { title ->
            (q.isEmpty() || fold(title.name).contains(q)) && (tag == null || tag in title.tags)
        }
        return when (sort) {
            HomeSort.MANUAL -> filtered
            // 満タンの時刻が早い(もう満タンの物を含む)タイトルを上に。スタミナが無いタイトルは最後
            HomeSort.STAMINA -> filtered.sortedBy { earliestFull(roster, it.id) ?: Instant.MAX }
            // まだ終わっていない、対応済みでないイベントの期限が早いタイトルを上に
            HomeSort.EVENT -> filtered.sortedBy { title ->
                roster.events
                    .filter { it.gameTitleId == title.id && !it.isCompleted && it.endAt.isAfter(now) }
                    .minOfOrNull { it.endAt } ?: Instant.MAX
            }
        }
    }

    private fun earliestFull(roster: Roster, titleId: Long): Instant? {
        val accountIds = roster.accountsOf(titleId).map { it.id }.toSet()
        return roster.staminas.filter { it.gameAccountId in accountIds }.mapNotNull(Stamina::fullAt).minOrNull()
    }

    /** 使われているタグ。最初に出てきた順。 */
    fun allTags(roster: Roster): List<String> = roster.activeTitles.flatMap { it.tags }.distinct()

    /** 全角と半角、大文字と小文字の違いで検索に漏れないよう、揃えてから比べる。 */
    private fun fold(s: String): String = Normalizer.normalize(s.trim(), Normalizer.Form.NFKC).lowercase()
}
