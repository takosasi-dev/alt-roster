@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.takosasidev.altroster.ui.gacha

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.takosasidev.altroster.ui.common.groupShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.calc.Spending
import io.github.takosasidev.altroster.core.model.GachaLog
import io.github.takosasidev.altroster.core.model.Roster
import io.github.takosasidev.altroster.ui.RosterViewModel
import io.github.takosasidev.altroster.ui.common.ConfirmDialog
import io.github.takosasidev.altroster.ui.common.DatePickDialog
import io.github.takosasidev.altroster.ui.common.NumberField
import io.github.takosasidev.altroster.ui.common.TitleAvatar
import java.time.LocalDate
import java.time.YearMonth

const val NEW_GACHA_ID = -1L

/** 課金・ガチャの記録の一覧。押すと編集、ごみ箱で(確認のあと)削除。 */
@Composable
fun GachaListScreen(vm: RosterViewModel, onEdit: (Long) -> Unit) {
    val roster = vm.roster.collectAsStateWithLifecycle().value
    var deleting by remember { mutableStateOf<GachaLog?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text("課金の記録") }, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(NEW_GACHA_ID) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("記録する") },
            )
        },
    ) { padding ->
        if (roster == null) return@Scaffold
        val thisMonth = Spending.monthTotal(roster, YearMonth.from(vm.now().atZone(vm.zone())))
        // 一覧は新しい順なので、月ごとに分けても順番は崩れない
        val byMonth = remember(roster.gachaLogs) { roster.gachaLogs.groupBy { YearMonth.from(it.date) } }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item(key = "month") {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                        Text("今月の合計", style = MaterialTheme.typography.labelLarge)
                        Text(Format.yen(thisMonth.total), style = MaterialTheme.typography.displaySmallEmphasized)
                        Text("${thisMonth.count} 件", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (roster.gachaLogs.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "まだ記録がありません。右下の「記録する」か、アプリのアイコンの長押しから入力できます。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            byMonth.forEach { (month, logs) ->
                item(key = "h$month") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)) {
                        Text(
                            "${month.year}年${month.monthValue}月",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Format.yen(logs.sumOf { it.amount.toLong() }),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(logs, key = { _, it -> it.id }) { index, log ->
                    LogRow(roster, log, groupShape(index, logs.size), onEdit = { onEdit(log.id) }, onDelete = { deleting = log })
                }
            }
        }
    }

    deleting?.let { log ->
        ConfirmDialog(
            title = "この記録を削除しますか",
            text = "${log.date} ${Format.yen(log.amount.toLong())}。この操作は取り消せません。",
            confirmLabel = "削除する",
            onConfirm = {
                deleting = null
                vm.launch { deleteGachaLog(log.id) }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** 記録の 1 行。金額を右に大きく出し、左はタイトルの目印と、アカウント・日付・通貨・メモ。 */
@Composable
private fun LogRow(roster: Roster, log: GachaLog, shape: Shape, onEdit: () -> Unit, onDelete: () -> Unit) {
    val title = roster.titleOfAccount(log.gameAccountId)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "編集", onClick = onEdit).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title != null) TitleAvatar(title, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                Text(
                    roster.accountDisplayName(log.gameAccountId),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull("${log.date.year}/${log.date.monthValue}/${log.date.dayOfMonth}", log.currencyType, log.memo)
                        .joinToString(" ・ "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(Format.yen(log.amount.toLong()), style = MaterialTheme.typography.titleMediumEmphasized)
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 課金・ガチャの記録の入力。アイコンの長押しのショートカットからも、ここへ直接来る。
 * 金額を打って保存するだけで済むよう、アカウントは前に使った物、日付は今日を入れておく。
 */
@Composable
fun GachaEntryScreen(vm: RosterViewModel, logId: Long, onDone: () -> Unit) {
    val roster = vm.roster.collectAsStateWithLifecycle().value
    val haptic = LocalHapticFeedback.current
    val existing = roster?.gachaLogs?.find { it.id == logId }

    var loaded by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf<LocalDate>(vm.now().atZone(vm.zone()).toLocalDate()) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var accountMenu by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    // 記録が読めたら 1 回だけ、入力欄の初めの値を入れる
    LaunchedEffect(roster != null) {
        if (roster != null && !loaded) {
            loaded = true
            if (existing != null) {
                accountId = existing.gameAccountId
                date = existing.date
                amount = existing.amount.toString()
                currency = existing.currencyType.orEmpty()
                memo = existing.memo.orEmpty()
            } else {
                // 一覧は新しい順なので、先頭が前に使ったアカウント
                val last = roster.gachaLogs.firstOrNull()?.gameAccountId
                accountId = roster.activeAccounts.find { it.id == last }?.id ?: roster.activeAccounts.firstOrNull()?.id
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (logId == NEW_GACHA_ID) "課金を記録" else "記録を編集") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
            )
        },
    ) { padding ->
        if (roster == null) return@Scaffold
        // 編集のときは、アーカイブしたアカウントの記録でも選べるようにする
        val current = existing?.let { roster.accountById[it.gameAccountId] }
        val choices = (roster.activeAccounts + listOfNotNull(current)).distinctBy { it.id }
        if (choices.isEmpty()) {
            Text(
                "先にホームでタイトルを登録してください。課金の記録は、タイトルのアカウントにひもづけて残します。",
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }
        val amountInt = amount.toIntOrNull()
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box {
                PickRow(Icons.Outlined.Person, accountId?.let { roster.accountLabel(it) } ?: "アカウントを選ぶ", groupShape(0, 2)) {
                    accountMenu = true
                }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    choices.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(roster.accountLabel(account.id)) },
                            onClick = {
                                accountId = account.id
                                accountMenu = false
                            },
                        )
                    }
                }
            }
            PickRow(Icons.Outlined.CalendarMonth, "日付: ${date.year}/${date.monthValue}/${date.dayOfMonth}", groupShape(1, 2)) {
                pickingDate = true
            }
            }
            NumberField(amount, { amount = it }, "金額", suffix = "円")
            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.take(30) },
                label = { Text("使ったゲーム内の通貨(任意)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it.take(300) },
                label = { Text("メモ(何が出たか など・任意)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = accountId != null && amountInt != null,
                onClick = {
                    val account = accountId
                    if (account != null && amountInt != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val base = existing ?: GachaLog(gameAccountId = account, date = date, amount = amountInt, createdAt = vm.now())
                        val log = base.copy(
                            gameAccountId = account,
                            date = date,
                            amount = amountInt,
                            currencyType = currency.trim().ifEmpty { null },
                            memo = memo.trim().ifEmpty { null },
                        )
                        vm.launch { saveGachaLog(log) }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
            ) { Text("保存", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (pickingDate) {
        DatePickDialog(date, onConfirm = { date = it; pickingDate = false }, onDismiss = { pickingDate = false })
    }
}

/** 押すと選ぶ画面が開く行(行頭に丸いアイコン、右に矢印)。 */
@Composable
private fun PickRow(icon: ImageVector, text: String, shape: Shape, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp)) }
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** アカウントを選ぶ一覧に出す名前。いつも「タイトル / アカウント」の両方を出す。 */
private fun Roster.accountLabel(accountId: Long): String {
    val account = accountById[accountId] ?: return ""
    return "${titleById[account.gameTitleId]?.name.orEmpty()} / ${account.label}"
}
