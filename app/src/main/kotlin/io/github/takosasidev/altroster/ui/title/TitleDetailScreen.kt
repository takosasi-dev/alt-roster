@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.takosasidev.altroster.ui.title

import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Shape
import io.github.takosasidev.altroster.ui.common.groupShape

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.calc.DeleteImpacts
import io.github.takosasidev.altroster.core.calc.HomeList
import io.github.takosasidev.altroster.core.calc.TaskReset
import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.GameAccount
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.ui.RosterViewModel
import io.github.takosasidev.altroster.ui.common.AccountEditDialog
import io.github.takosasidev.altroster.ui.common.ConfirmDialog
import io.github.takosasidev.altroster.ui.common.EventEditDialog
import io.github.takosasidev.altroster.ui.common.SectionHeader
import io.github.takosasidev.altroster.ui.common.StaminaEditDialog
import io.github.takosasidev.altroster.ui.common.StaminaGauge
import io.github.takosasidev.altroster.ui.common.TaskCheckRow
import io.github.takosasidev.altroster.ui.common.TaskEditDialog
import io.github.takosasidev.altroster.ui.common.TitleAvatar
import io.github.takosasidev.altroster.ui.common.TitleEditDialog
import io.github.takosasidev.altroster.ui.common.rememberNow
import io.github.takosasidev.altroster.ui.common.resetLabel
import io.github.takosasidev.altroster.ui.home.EventRow

/** いま開いているダイアログ。1 度に 1 つだけ。 */
private sealed interface Sheet {
    data object EditTitle : Sheet
    data object DeleteTitle : Sheet
    data class EditAccount(val account: GameAccount?) : Sheet
    data class DeleteAccount(val account: GameAccount) : Sheet
    data class EditStamina(val accountId: Long, val resource: StaminaResource?) : Sheet
    data class DeleteStamina(val resource: StaminaResource) : Sheet
    data class EditTask(val accountId: Long, val task: DailyTask?) : Sheet
    data class DeleteTask(val task: DailyTask) : Sheet
    data class EditEvent(val event: GameEvent?) : Sheet
    data class DeleteEvent(val event: GameEvent) : Sheet
}

/**
 * タイトルの詳細。アカウント・スタミナ・タスク・イベントの登録、編集、削除、アーカイブをここで行う。
 * 消すときは、一緒に失われる記録の件数を確認の画面に出す。
 */
@Composable
fun TitleDetailScreen(vm: RosterViewModel, titleId: Long, onBack: () -> Unit) {
    val roster = vm.roster.collectAsStateWithLifecycle().value
    val now = rememberNow(vm::now)
    val zone = vm.zone()
    val title = roster?.titleById?.get(titleId)
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
                actions = {
                    if (title != null) {
                        IconButton(onClick = { sheet = Sheet.EditTitle }) { Icon(Icons.Filled.Edit, contentDescription = "タイトルを編集") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "メニュー") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (title.isArchived) "アーカイブから戻す" else "アーカイブする(一覧から隠す)") },
                                    onClick = {
                                        menu = false
                                        vm.launch { saveTitle(title.copy(isArchived = !title.isArchived)) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("削除する", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menu = false
                                        sheet = Sheet.DeleteTitle
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (roster == null || title == null) {
            // 読み込み中か、消した直後。消した直後は、呼び出し元が前の画面へ戻す
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val accent = Color(title.colorArgb)
        val checked = remember(roster, now, zone) { TaskReset.checkedTaskIds(roster, now, zone) }
        val accounts = roster.accountsOf(title.id, includeArchived = true)
        val events = roster.events.filter { it.gameTitleId == title.id }.sortedBy { it.endAt }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TitleAvatar(title, size = 64.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(title.name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (title.isArchived) {
                        Text("アーカイブ済み(一覧と通知から外しています)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        title.tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }

            SectionHeader("アカウント")
            accounts.forEachIndexed { index, account ->
                AccountCard(
                    shape = groupShape(index, accounts.size),
                    account = account,
                    staminas = roster.staminas.filter { it.gameAccountId == account.id },
                    tasks = roster.tasks.filter { it.gameAccountId == account.id }.sortedWith(compareBy({ it.sortOrder }, { it.id })),
                    checked = checked,
                    accent = accent,
                    now = now,
                    zone = zone,
                    onSheet = { sheet = it },
                    onToggleArchive = { vm.launch { saveAccount(account.copy(isArchived = !account.isArchived)) } },
                    onToggleTask = { task, value -> vm.launch { setTaskChecked(task, value) } },
                )
            }
            TextButton(onClick = { sheet = Sheet.EditAccount(null) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("アカウントを追加", modifier = Modifier.padding(start = 4.dp))
            }

            SectionHeader("イベント")
            if (events.isEmpty()) {
                Text("イベントの期限を登録すると、残り時間と終了前の通知が出ます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            events.forEachIndexed { index, event ->
                Surface(shape = groupShape(index, events.size), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = event.isCompleted,
                            onCheckedChange = { value -> vm.launch { saveEvent(event.copy(isCompleted = value)) } },
                        )
                        Column(Modifier.weight(1f)) {
                            EventRow(event, Format.dateTime(event.endAt, zone) + " まで" + if (event.isCompleted) " ・ 対応済み" else "", now, onComplete = null)
                        }
                        IconButton(onClick = { sheet = Sheet.EditEvent(event) }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "イベントを編集", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onClick = { sheet = Sheet.EditEvent(null) }, modifier = Modifier.padding(bottom = 32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("イベントを追加", modifier = Modifier.padding(start = 4.dp))
            }
        }

        // ---- ダイアログ ----------------------------------------------------
        val close = { sheet = null }
        when (val s = sheet) {
            null -> Unit
            Sheet.EditTitle -> TitleEditDialog(
                initial = title,
                knownTags = HomeList.allTags(roster),
                onSave = {
                    close()
                    vm.launch { saveTitle(it) }
                },
                onDismiss = close,
            )
            Sheet.DeleteTitle -> ConfirmDialog(
                title = "「${title.name}」を削除しますか",
                text = lossText(DeleteImpacts.forTitle(roster, title.id).describe()) +
                    "\n記録を残したまま一覧から隠したいときは、アーカイブを使ってください。",
                confirmLabel = "削除する",
                onConfirm = {
                    close()
                    onBack()
                    vm.launch { deleteTitle(title.id) }
                },
                onDismiss = close,
            )
            is Sheet.EditAccount -> AccountEditDialog(
                initial = s.account,
                titleId = title.id,
                now = vm::now,
                onSave = {
                    close()
                    vm.launch { saveAccount(it) }
                },
                onDismiss = close,
            )
            is Sheet.DeleteAccount -> ConfirmDialog(
                title = "アカウント「${s.account.label}」を削除しますか",
                text = lossText(DeleteImpacts.forAccount(roster, s.account.id).describe()),
                confirmLabel = "削除する",
                onConfirm = {
                    close()
                    vm.launch { deleteAccount(s.account.id) }
                },
                onDismiss = close,
            )
            is Sheet.EditStamina -> StaminaEditDialog(
                initial = s.resource,
                accountId = s.accountId,
                now = vm::now,
                onSave = {
                    close()
                    vm.launch { saveStamina(it) }
                },
                onDelete = s.resource?.let { r -> { sheet = Sheet.DeleteStamina(r) } },
                onDismiss = close,
            )
            is Sheet.DeleteStamina -> ConfirmDialog(
                title = "スタミナ「${s.resource.resourceName}」を削除しますか",
                text = "このスタミナの設定と通知が消えます。",
                confirmLabel = "削除する",
                onConfirm = {
                    close()
                    vm.launch { deleteStamina(s.resource.id) }
                },
                onDismiss = close,
            )
            is Sheet.EditTask -> TaskEditDialog(
                initial = s.task,
                accountId = s.accountId,
                onSave = {
                    close()
                    vm.launch { saveTask(it) }
                },
                onDelete = s.task?.let { t -> { sheet = Sheet.DeleteTask(t) } },
                onDismiss = close,
            )
            is Sheet.DeleteTask -> ConfirmDialog(
                title = "タスク「${s.task.label}」を削除しますか",
                text = lossText(DeleteImpacts.forTask(roster, s.task.id).describe()),
                confirmLabel = "削除する",
                onConfirm = {
                    close()
                    vm.launch { deleteTask(s.task.id) }
                },
                onDismiss = close,
            )
            is Sheet.EditEvent -> EventEditDialog(
                initial = s.event,
                titleId = title.id,
                now = vm::now,
                zone = zone,
                onSave = {
                    close()
                    vm.launch { saveEvent(it) }
                },
                onDelete = s.event?.let { e -> { sheet = Sheet.DeleteEvent(e) } },
                onDismiss = close,
            )
            is Sheet.DeleteEvent -> ConfirmDialog(
                title = "イベント「${s.event.name}」を削除しますか",
                text = "このイベントの期限と通知が消えます。",
                confirmLabel = "削除する",
                onConfirm = {
                    close()
                    vm.launch { deleteEvent(s.event.id) }
                },
                onDismiss = close,
            )
        }
    }
}

/** 「◯件の記録が失われます」の文。一緒に消える物が無ければ、その旨を書く。 */
private fun lossText(described: String): String =
    if (described.isEmpty()) "ひもづく記録はありません。この操作は取り消せません。"
    else "次の記録も一緒に失われます。この操作は取り消せません。\n$described"

@Composable
private fun AccountCard(
    shape: Shape,
    account: GameAccount,
    staminas: List<StaminaResource>,
    tasks: List<DailyTask>,
    checked: Set<Long>,
    accent: Color,
    now: java.time.Instant,
    zone: java.time.ZoneId,
    onSheet: (Sheet) -> Unit,
    onToggleArchive: () -> Unit,
    onToggleTask: (DailyTask, Boolean) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = shape) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(account.label, style = MaterialTheme.typography.titleMediumEmphasized)
                    if (account.isArchived) {
                        Text("アーカイブ済み", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    account.loginHintMemo?.let {
                        Text("メモ: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "アカウントのメニュー") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("編集") }, onClick = { menu = false; onSheet(Sheet.EditAccount(account)) })
                        DropdownMenuItem(
                            text = { Text(if (account.isArchived) "アーカイブから戻す" else "アーカイブする") },
                            onClick = { menu = false; onToggleArchive() },
                        )
                        DropdownMenuItem(
                            text = { Text("削除する", color = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onSheet(Sheet.DeleteAccount(account)) },
                        )
                    }
                }
            }

            staminas.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StaminaGauge(r, accent, now, zone, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onSheet(Sheet.EditStamina(account.id, r)) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "${r.resourceName} を編集", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            tasks.forEach { task ->
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskCheckRow(task.label, resetLabel(task), task.id in checked, { onToggleTask(task, it) }, Modifier.weight(1f))
                    IconButton(onClick = { onSheet(Sheet.EditTask(account.id, task)) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "${task.label} を編集", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row {
                TextButton(onClick = { onSheet(Sheet.EditStamina(account.id, null)) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("スタミナ", modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = { onSheet(Sheet.EditTask(account.id, null)) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("タスク", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
