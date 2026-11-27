@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.takosasidev.altroster.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.takosasidev.altroster.ui.common.groupShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.calc.Digest
import io.github.takosasidev.altroster.core.calc.DigestBuilder
import io.github.takosasidev.altroster.core.calc.EventTime
import io.github.takosasidev.altroster.core.calc.HomeList
import io.github.takosasidev.altroster.core.calc.HomeSort
import io.github.takosasidev.altroster.core.calc.Stamina
import io.github.takosasidev.altroster.core.calc.TaskReset
import io.github.takosasidev.altroster.core.calc.Urgency
import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.GameAccount
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.Roster
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.ui.RosterViewModel
import io.github.takosasidev.altroster.ui.common.EmptyState
import io.github.takosasidev.altroster.ui.common.StaminaGauge
import io.github.takosasidev.altroster.ui.common.StaminaQuickDialog
import io.github.takosasidev.altroster.ui.common.TaskCheckRow
import io.github.takosasidev.altroster.ui.common.TitleAvatar
import io.github.takosasidev.altroster.ui.common.TitleEditDialog
import io.github.takosasidev.altroster.ui.common.rememberNow
import io.github.takosasidev.altroster.ui.common.resetLabel
import io.github.takosasidev.altroster.ui.theme.urgencyColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** ホームで「満タン間近」として上に出す範囲。これより先に満タンになる物は、タイトルのカードの中で見る。 */
private val STAMINA_SOON: Duration = Duration.ofHours(1)

/**
 * 全部のタイトルを横断するホーム。
 *
 * いちばん上に、数だけの帯(いつも見える)と「今すぐ気にすること」のカードを置き、
 * それ以外はタイトルごとのカードに畳む。タスクは上のカードから 1 回押すだけでチェックできる。
 */
@Composable
fun HomeScreen(vm: RosterViewModel, onOpenTitle: (Long) -> Unit) {
    val roster = vm.roster.collectAsStateWithLifecycle().value
    val now = rememberNow(vm::now)
    val zone = vm.zone()

    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(HomeSort.MANUAL) }
    var sortMenu by remember { mutableStateOf(false) }
    var addingTitle by rememberSaveable { mutableStateOf(false) }
    var quickStaminaId by rememberSaveable { mutableStateOf<Long?>(null) }
    // この画面でチェックしたタスク。すぐ消さず、取り消し線を付けたまま残す(押し間違いを戻せるように)
    val recentlyChecked = remember { mutableStateListOf<Long>() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        // 検索中は普通のバーなので、縮む動きをつながない(つなぐと最初のスワイプが見えない縮みに取られる)
        modifier = if (searching) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val actions: @Composable RowScope.() -> Unit = {
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) query = ""
                    }) {
                        Icon(
                            if (searching) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searching) "検索をやめる" else "検索",
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "並べ替え")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            HomeSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = {
                                        if (option == sort) Icon(Icons.Filled.CheckCircle, contentDescription = "選択中")
                                    },
                                    onClick = {
                                        sort = option
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
            }
            if (searching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("タイトルの名前で検索") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = actions,
                )
            } else {
                // 大きな見出し。スクロールすると細いバーに縮む
                LargeFlexibleTopAppBar(title = { Text("AltRoster") }, actions = actions, scrollBehavior = scrollBehavior)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingTitle = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("タイトル") },
            )
        },
    ) { padding ->
        when {
            roster == null -> Box(Modifier.fillMaxSize())
            roster.activeTitles.isEmpty() -> EmptyState(
                message = "まだタイトルがありません。\n遊んでいるゲームの名前を登録すると、スタミナ・期限・デイリーをここでまとめて見られます。",
                actionLabel = "タイトルを追加",
                onAction = { addingTitle = true },
                modifier = Modifier.padding(padding),
            )
            else -> {
                val digest = remember(roster, now, zone) { DigestBuilder.build(roster, now, zone, staminaWithin = STAMINA_SOON) }
                val checked = remember(roster, now, zone) { TaskReset.checkedTaskIds(roster, now, zone) }
                val tags = remember(roster) { HomeList.allTags(roster) }
                // 選んでいたタグがどのタイトルからも外れたら、絞り込みも外す
                // (タグが 1 つも無くなるとチップの列ごと消えて、外す手段が無くなるため)
                if (tag != null && tag !in tags) tag = null
                val titles = remember(roster, query, tag, sort, now) { HomeList.apply(roster, query, tag, sort, now) }
                val toggleTask: (DailyTask, Boolean) -> Unit = { task, value ->
                    if (value) recentlyChecked.add(task.id) else recentlyChecked.remove(task.id)
                    vm.launch { setTaskChecked(task, value) }
                }

                Column(Modifier.padding(padding).fillMaxSize()) {
                    SummaryBar(digest)
                    // タイトルのカードは 1 つのまとまりとして見せる(端だけ大きく丸め、間は細い隙間)
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        item(key = "digest") {
                            DigestCard(
                                digest, roster, checked, recentlyChecked, now, zone, toggleTask,
                                modifier = Modifier.padding(bottom = 9.dp),
                            ) { quickStaminaId = it }
                        }
                        if (tags.isNotEmpty()) {
                            item(key = "tags") {
                                Row(
                                    Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 9.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FilterChip(selected = tag == null, onClick = { tag = null }, label = { Text("すべて") })
                                    tags.forEach { t ->
                                        FilterChip(
                                            selected = tag == t,
                                            onClick = { tag = if (tag == t) null else t },
                                            label = { Text(t) },
                                        )
                                    }
                                }
                            }
                        }
                        if (titles.isEmpty()) {
                            item(key = "none") {
                                Text(
                                    "条件に合うタイトルがありません。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        itemsIndexed(titles, key = { _, it -> it.id }) { index, title ->
                            TitleCard(
                                title = title,
                                shape = groupShape(index, titles.size),
                                roster = roster,
                                checked = checked,
                                now = now,
                                zone = zone,
                                canReorder = sort == HomeSort.MANUAL && query.isBlank() && tag == null,
                                onOpen = { onOpenTitle(title.id) },
                                onMove = { up -> vm.launch { moveTitle(title.id, up) } },
                                onToggleTask = toggleTask,
                                onQuickStamina = { quickStaminaId = it },
                                onCompleteEvent = { event -> vm.launch { saveEvent(event.copy(isCompleted = true)) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (addingTitle) {
        TitleEditDialog(
            initial = null,
            knownTags = roster?.let(HomeList::allTags).orEmpty(),
            onSave = { title ->
                addingTitle = false
                // 作ったらすぐ中身(アカウントやスタミナ)を足せるよう、そのタイトルの画面へ進む。
                // アカウントが無いと何も登録できないので、最初の 1 つを一緒に作っておく
                vm.launch {
                    val id = saveTitle(title)
                    saveAccount(GameAccount(gameTitleId = id, label = "メイン", createdAt = vm.now()))
                    onOpenTitle(id)
                }
            },
            onDismiss = { addingTitle = false },
        )
    }
    roster?.staminas?.find { it.id == quickStaminaId }?.let { resource ->
        StaminaQuickDialog(
            resource = resource,
            now = vm::now,
            onSave = {
                quickStaminaId = null
                vm.launch { saveStamina(it) }
            },
            onDismiss = { quickStaminaId = null },
        )
    }
}

/**
 * いつも見える、数だけの帯。細かい通知を追いかけなくても、ここを見れば足りると分かるようにする。
 * 数の横に短い名前も書く(アイコンの形だけに頼らない)。
 */
@Composable
private fun SummaryBar(digest: Digest) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)) {
        Text(
            "本日のダイジェスト",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Count(Icons.Filled.Bolt, "満タン間近のスタミナ", "満タン間近", digest.staminas.size, Modifier.weight(1f))
            Count(Icons.Filled.Alarm, "24 時間以内に終わるイベント", "24時間以内", digest.events.size, Modifier.weight(1f))
            Count(Icons.Filled.TaskAlt, "未消化のタスク", "未消化", digest.uncheckedTasks.size, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Count(icon: ImageVector, description: String, label: String, count: Int, modifier: Modifier) {
    val active = count > 0
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 今すぐ気にすること。満タン間近のスタミナ、24 時間以内のイベント、未消化のタスク。 */
@Composable
private fun DigestCard(
    digest: Digest,
    roster: Roster,
    checked: Set<Long>,
    recentlyChecked: List<Long>,
    now: Instant,
    zone: ZoneId,
    onToggleTask: (DailyTask, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onQuickStamina: (Long) -> Unit,
) {
    // 未消化の物と、この画面でいまチェックした物
    val tasks = roster.activeTasks.filter { it.id !in checked || it.id in recentlyChecked }

    Card(
        modifier,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "今すぐ気にすること",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (digest.staminas.isEmpty() && digest.events.isEmpty() && tasks.isEmpty()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("いまは特にありません。", modifier = Modifier.padding(start = 8.dp))
                }
            }
            digest.staminas.forEach { s ->
                val accent = Color(roster.titleOfAccount(s.resource.gameAccountId)?.colorArgb ?: 0)
                StaminaGauge(
                    resource = s.resource,
                    accent = accent,
                    now = now,
                    zone = zone,
                    label = "${roster.accountDisplayName(s.resource.gameAccountId)} ${s.resource.resourceName}",
                    onClick = { onQuickStamina(s.resource.id) },
                )
            }
            digest.events.forEach { e -> EventRow(e, roster.titleById[e.gameTitleId]?.name, now, onComplete = null) }
            tasks.forEach { task ->
                TaskCheckRow(
                    label = task.label,
                    sub = "${roster.accountDisplayName(task.gameAccountId)} ・ ${resetLabel(task)}",
                    checked = task.id in checked,
                    onCheckedChange = { onToggleTask(task, it) },
                )
            }
        }
    }
}

/**
 * イベントの 1 行。残り時間に応じて色が変わる。色だけに頼らないよう、アイコンの形も変える
 * (通常 = カレンダー、3 日以内 = 時計、24 時間以内 = 警告の三角)。
 */
@Composable
fun EventRow(event: GameEvent, titleName: String?, now: Instant, onComplete: (() -> Unit)?) {
    val urgency = EventTime.urgency(event, now)
    val color by animateColorAsState(urgencyColor(urgency), tween(600), label = "countdown")
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        // 記号は同じ色の淡い丸に載せる
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (urgency) {
                    Urgency.URGENT -> Icons.Filled.Warning
                    Urgency.WARNING -> Icons.Filled.Schedule
                    else -> Icons.Filled.Event
                },
                contentDescription = when (urgency) {
                    Urgency.URGENT -> "24 時間以内"
                    Urgency.WARNING -> "3 日以内"
                    Urgency.ENDED -> "終了"
                    Urgency.NORMAL -> null
                },
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(event.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val left = if (urgency == Urgency.ENDED) "終了しました" else "あと ${Format.duration(EventTime.remaining(event, now))}"
            Text(
                listOfNotNull(titleName, left).joinToString(" ・ "),
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
        if (onComplete != null) {
            TextButton(onClick = onComplete) { Text("対応済み") }
        }
    }
}

/** タイトルごとのカード。畳んだ状態では、いちばん満タンに近いスタミナ 1 本と件数だけを出す。 */
@Composable
private fun TitleCard(
    title: GameTitle,
    shape: Shape,
    roster: Roster,
    checked: Set<Long>,
    now: Instant,
    zone: ZoneId,
    canReorder: Boolean,
    onOpen: () -> Unit,
    onMove: (up: Boolean) -> Unit,
    onToggleTask: (DailyTask, Boolean) -> Unit,
    onQuickStamina: (Long) -> Unit,
    onCompleteEvent: (GameEvent) -> Unit,
) {
    var expanded by rememberSaveable(title.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val accent = Color(title.colorArgb)
    val accounts = roster.accountsOf(title.id)
    val accountIds = accounts.map { it.id }.toSet()
    val staminas = roster.staminas.filter { it.gameAccountId in accountIds }
    val tasks = roster.activeTasks.filter { it.gameAccountId in accountIds }
    val events = roster.events
        .filter { it.gameTitleId == title.id && !it.isCompleted && it.endAt.isAfter(now) }
        .sortedBy { it.endAt }
    val unchecked = tasks.count { it.id !in checked }
    // 複数のスタミナがあるときに先に見せるのは、いちばん早く満タンになる物
    val primary: StaminaResource? = staminas.minByOrNull { Stamina.fullAt(it) ?: Instant.MAX }

    Card(shape = shape) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (expanded) "畳む" else "開く") { expanded = !expanded }
                    .padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TitleAvatar(title, size = 44.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(title.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val summary = listOfNotNull(
                        if (unchecked > 0) "未消化 $unchecked 件" else null,
                        events.firstOrNull()?.let { "期限 あと ${Format.duration(EventTime.remaining(it, now))}" },
                        title.tags.takeIf { it.isNotEmpty() }?.joinToString(" ") { "#$it" },
                    ).joinToString(" ・ ")
                    if (summary.isNotEmpty()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "メニュー") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("詳細・編集") }, onClick = { menu = false; onOpen() })
                        if (canReorder) {
                            DropdownMenuItem(text = { Text("上へ動かす") }, onClick = { menu = false; onMove(true) })
                            DropdownMenuItem(text = { Text("下へ動かす") }, onClick = { menu = false; onMove(false) })
                        }
                    }
                }
            }

            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
                if (!expanded && primary != null) {
                    StaminaGauge(
                        resource = primary,
                        accent = accent,
                        now = now,
                        zone = zone,
                        label = if (accounts.size > 1) {
                            "${roster.accountById[primary.gameAccountId]?.label.orEmpty()} ${primary.resourceName}"
                        } else {
                            primary.resourceName
                        },
                        onClick = { onQuickStamina(primary.id) },
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column {
                        accounts.forEach { account ->
                            if (accounts.size > 1) {
                                Text(
                                    account.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            staminas.filter { it.gameAccountId == account.id }.forEach { r ->
                                StaminaGauge(r, accent, now, zone, onClick = { onQuickStamina(r.id) })
                            }
                            tasks.filter { it.gameAccountId == account.id }.forEach { task ->
                                TaskCheckRow(task.label, resetLabel(task), task.id in checked, { onToggleTask(task, it) })
                            }
                        }
                        events.forEach { e -> EventRow(e, null, now, onComplete = { onCompleteEvent(e) }) }
                        if (staminas.isEmpty() && tasks.isEmpty() && events.isEmpty()) {
                            Text(
                                "スタミナ・タスク・イベントは「詳細・編集」から登録します。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        TextButton(onClick = onOpen) { Text("詳細・編集") }
                    }
                }
            }
        }
    }
}
