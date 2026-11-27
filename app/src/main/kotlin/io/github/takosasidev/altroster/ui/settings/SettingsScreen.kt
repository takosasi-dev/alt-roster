@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.takosasidev.altroster.ui.settings

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DarkMode
import io.github.takosasidev.altroster.core.model.ThemeColorMode
import io.github.takosasidev.altroster.data.backup.BackupManager
import io.github.takosasidev.altroster.notify.AlarmScheduler
import io.github.takosasidev.altroster.notify.Notifier
import io.github.takosasidev.altroster.ui.RosterViewModel
import io.github.takosasidev.altroster.ui.common.ColorPicker
import io.github.takosasidev.altroster.ui.common.TimePickDialog
import io.github.takosasidev.altroster.ui.common.TitleAvatar
import io.github.takosasidev.altroster.ui.common.groupShape

@Composable
fun SettingsScreen(vm: RosterViewModel, onOpenBackup: () -> Unit, onOpenTitle: (Long) -> Unit) {
    val context = LocalContext.current
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: AppSettings()
    val roster = vm.roster.collectAsStateWithLifecycle().value
    var pickingDigestTime by remember { mutableStateOf(false) }

    // 通知と正確なアラームの許可は、端末の設定の画面で変えて戻ってくるので、戻るたびに読み直す
    var canNotify by remember { mutableStateOf(Notifier.canPost(context)) }
    var canExact by remember { mutableStateOf(AlarmScheduler.canScheduleExact(context)) }
    LifecycleResumeEffect(Unit) {
        canNotify = Notifier.canPost(context)
        canExact = AlarmScheduler.canScheduleExact(context)
        // 許可が変わっていれば、正確さを変えてアラームを仕込み直す
        vm.launch { updateSettings { } }
        onPauseOrDispose { }
    }
    val askNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        // 前に断っていると、もう聞く画面は出ない。そのときは端末の設定の画面を開く
        if (!granted) openNotificationSettings(context)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text("設定") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 24.dp),
        ) {
            Group(
                "通知",
                buildList<@Composable () -> Unit> {
                    add {
                        ActionRow(
                            title = if (canNotify) "通知: 許可されています" else "通知: 許可されていません",
                            note = if (canNotify) null else "スタミナ・期限・ダイジェストの通知は、許可するまで出ません。",
                            action = if (canNotify) null else "許可する",
                            icon = if (canNotify) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                            onAction = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openNotificationSettings(context)
                                }
                            },
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add {
                            ActionRow(
                                title = if (canExact) "正確な時刻の通知: 使えます" else "正確な時刻の通知: 使えません",
                                note = if (canExact) null else "許可が無い間は、通知が数分〜十数分ほど遅れることがあります。",
                                action = if (canExact) null else "設定を開く",
                                icon = Icons.Outlined.Alarm,
                                onAction = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    add {
                        SwitchRow(
                            title = "1 日 1 回のダイジェスト通知",
                            note = "満タンの予定・24 時間以内の期限・未消化のタスクを 1 件にまとめます。知らせる事が無い日は出しません。",
                            checked = settings.digestNotificationEnabled,
                            icon = Icons.Outlined.Summarize,
                            onCheckedChange = { value -> vm.launch { updateSettings { setDigestEnabled(value) } } },
                        )
                    }
                    add {
                        ActionRow(
                            title = "ダイジェストの時刻: ${settings.digestNotificationTime}",
                            note = null,
                            action = "変える",
                            icon = Icons.Outlined.Schedule,
                            onAction = { pickingDigestTime = true },
                        )
                    }
                    add {
                        SwitchRow(
                            title = "タスクがリセットされたら通知する",
                            note = "同じ時刻にリセットされたタスクは 1 件にまとめます。",
                            checked = settings.resetNotificationEnabled,
                            icon = Icons.Outlined.RestartAlt,
                            onCheckedChange = { value -> vm.launch { updateSettings { setResetNotificationEnabled(value) } } },
                        )
                    }
                },
            )

            Group(
                "見た目",
                listOf<@Composable () -> Unit>(
                    {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("テーマの色", style = MaterialTheme.typography.bodyLarge)
                            Segments(
                                listOf(ThemeColorMode.DYNAMIC to "壁紙に合わせる", ThemeColorMode.MANUAL to "自分で選ぶ"),
                                settings.themeColorMode,
                            ) { mode -> vm.launch { updateSettings { setThemeColorMode(mode) } } }
                            if (settings.themeColorMode == ThemeColorMode.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                Text(
                                    "この端末(Android 11 以前)は壁紙に合わせた色に対応していないため、標準の色で表示します。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (settings.themeColorMode == ThemeColorMode.MANUAL) {
                                ColorPicker(
                                    selectedArgb = settings.manualThemeColor,
                                    onSelect = { argb -> vm.launch { updateSettings { setManualThemeColor(argb) } } },
                                )
                            }
                        }
                    },
                    {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("明るさ", style = MaterialTheme.typography.bodyLarge)
                            Segments(
                                listOf(DarkMode.SYSTEM to "端末と同じ", DarkMode.LIGHT to "ライト", DarkMode.DARK to "ダーク"),
                                settings.darkMode,
                            ) { mode -> vm.launch { updateSettings { setDarkMode(mode) } } }
                        }
                    },
                ),
            )

            val secure = remember { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }
            Group(
                "ロック",
                listOf<@Composable () -> Unit> {
                    SwitchRow(
                        title = "アプリを開くときにロックを解除する",
                        note = if (secure) {
                            "端末の画面ロック(指紋・顔・PIN など)を使います。"
                        } else {
                            "端末に画面ロックが設定されていないため、入にしてもロックはかかりません。"
                        },
                        checked = settings.appLockEnabled,
                        icon = Icons.Outlined.Lock,
                        onCheckedChange = { value -> vm.launch { updateSettings { setAppLockEnabled(value) } } },
                    )
                },
            )

            Group(
                "データ",
                listOf<@Composable () -> Unit> {
                    NavRow(
                        icon = Icons.Outlined.Backup,
                        title = "バックアップと復元",
                        note = "記録は端末の中だけにあります。機種変更や故障に備えて、ときどき書き出してください。",
                        onClick = onOpenBackup,
                    )
                },
            )

            val archivedTitles = roster?.titles.orEmpty().filter { it.isArchived }
            val archivedAccounts = roster?.accounts.orEmpty().filter { a -> a.isArchived && roster?.titleById?.get(a.gameTitleId)?.isArchived == false }
            Group(
                "アーカイブした物",
                if (archivedTitles.isEmpty() && archivedAccounts.isEmpty()) {
                    listOf<@Composable () -> Unit> {
                        Text("ありません。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    archivedTitles.map { title ->
                        @Composable {
                            ArchivedRow(
                                name = title.name,
                                onOpen = { onOpenTitle(title.id) },
                                onRestore = { vm.launch { saveTitle(title.copy(isArchived = false)) } },
                            ) { TitleAvatar(title) }
                        }
                    } + archivedAccounts.map { account ->
                        @Composable {
                            val title = roster?.titleById?.get(account.gameTitleId)
                            ArchivedRow(
                                name = "${title?.name.orEmpty()} / ${account.label}",
                                onOpen = { onOpenTitle(account.gameTitleId) },
                                onRestore = { vm.launch { saveAccount(account.copy(isArchived = false)) } },
                            ) { if (title != null) TitleAvatar(title) else LeadingIcon(Icons.Outlined.Person) }
                        }
                    }
                },
            )

            Group(
                "このアプリについて",
                listOf<@Composable () -> Unit> {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        LeadingIcon(Icons.Outlined.Info)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text("AltRoster ${BackupManager.appVersionName(context)}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "通信は一切しません(インターネットの権限を持っていません)。ゲームのサーバーにはつながず、" +
                                    "ログインや操作の自動化も行いません。記録は手で入力した物だけです。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }
    }

    if (pickingDigestTime) {
        TimePickDialog(
            initial = settings.digestTime,
            onConfirm = { time ->
                pickingDigestTime = false
                vm.launch { updateSettings { setDigestTime(AppSettings.formatTime(time.hour, time.minute)) } }
            },
            onDismiss = { pickingDigestTime = false },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
}

/** 見出しと、角の丸いまとまり。行どうしは細い隙間で区切る(Android 16 の設定の画面と同じ形)。 */
@Composable
internal fun Group(title: String, rows: List<@Composable () -> Unit>) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEachIndexed { i, row ->
            Surface(Modifier.fillMaxWidth(), shape = groupShape(i, rows.size), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column { row() }
            }
        }
    }
}

/** 行の頭の丸いアイコン。 */
@Composable
internal fun LeadingIcon(icon: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp)) }
}

/** 名前と説明。行の真ん中に置く。[below] は説明の下に置く物(ボタンなど)。 */
@Composable
private fun RowText(title: String, note: String?, modifier: Modifier, below: @Composable () -> Unit = {}) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        below()
    }
}

@Composable
fun SwitchRow(title: String, note: String?, checked: Boolean, icon: ImageVector? = null, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LeadingIcon(icon)
        RowText(title, note, Modifier.weight(1f).padding(start = if (icon != null) 16.dp else 0.dp, end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun ActionRow(title: String, note: String?, action: String?, icon: ImageVector? = null, onAction: () -> Unit) {
    // ボタンは説明の下に置く(横に並べると、名前と説明が細く折り返されるため)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LeadingIcon(icon)
        RowText(title, note, Modifier.weight(1f).padding(start = if (icon != null) 16.dp else 0.dp)) {
            if (action != null) FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
        }
    }
}

/** 別の画面へ進む行。行のどこを押しても進み、右に矢印を出す。 */
@Composable
private fun NavRow(icon: ImageVector, title: String, note: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "開く", onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
        RowText(title, note, Modifier.weight(1f).padding(horizontal = 16.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 選んだ 1 つを地の色で示す切り替え。チェックの印は出さない(3 つ並ぶと文字が欠けるため)。 */
@Composable
internal fun <T> Segments(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun ArchivedRow(name: String, onOpen: () -> Unit, onRestore: () -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "開く", onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
        )
        TextButton(onClick = onRestore) { Text("戻す") }
    }
}
