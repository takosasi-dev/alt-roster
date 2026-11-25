package io.github.takosasidev.altroster.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.Roster
import io.github.takosasidev.altroster.data.RosterRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 全部の画面が共有する ViewModel。
 *
 * 記録は件数が少ないので、全部を 1 つの [Roster] として流し、画面ごとの絞り込みと計算は :core の関数に任せる。
 * 画面ごとに ViewModel を分けても、同じ流れを写すだけになるので 1 つにしている。
 */
class RosterViewModel(app: Application) : AndroidViewModel(app) {
    val repo: RosterRepository = RosterRepository.get(app)

    /** null は「まだ読めていない」。空の状態の案内が一瞬出るのを防ぐために区別する。 */
    val roster: StateFlow<Roster?> =
        repo.roster.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun now(): Instant = repo.now()
    fun zone(): ZoneId = repo.zone()

    /** 書き込み。画面を離れても最後まで終わるよう、ViewModel の寿命で走らせる。 */
    fun launch(block: suspend RosterRepository.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }
}
