@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
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
import io.github.takosasidev.altroster.ui.common.SectionHeader
import io.github.takosasidev.altroster.ui.common.TimePickDialog

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

    Scaffold(topBar = { TopAppBar(title = { Text("設定") }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader("通知")
            ActionRow(
                title = if (canNotify) "通知: 許可されています" else "通知: 許可されていません",
                note = if (canNotify) null else "スタミナ・期限・ダイジェストの通知は、許可するまで出ません。",
                action = if (canNotify) null else "許可する",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActionRow(
                    title = if (canExact) "正確な時刻の通知: 使えます" else "正確な時刻の通知: 使えません",
                    note = if (canExact) null else "許可が無い間は、通知が数分〜十数分ほど遅れることがあります。",
                    action = if (canExact) null else "設定を開く",
                    onAction = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    },
                )
            }
            SwitchRow(
                title = "1 日 1 回のダイジェスト通知",
                note = "満タンの予定・24 時間以内の期限・未消化のタスクを 1 件にまとめます。知らせる事が無い日は出しません。",
                checked = settings.digestNotificationEnabled,
                onCheckedChange = { value -> vm.launch { updateSettings { setDigestEnabled(value) } } },
            )
            ActionRow(
                title = "ダイジェストの時刻: ${settings.digestNotificationTime}",
                note = null,
                action = "変える",
                onAction = { pickingDigestTime = true },
            )
            SwitchRow(
                title = "タスクがリセットされたら通知する",
                note = "同じ時刻にリセットされたタスクは 1 件にまとめます。",
                checked = settings.resetNotificationEnabled,
                onCheckedChange = { value -> vm.launch { updateSettings { setResetNotificationEnabled(value) } } },
            )

            SectionHeader("見た目")
            Text("テーマの色", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.themeColorMode == ThemeColorMode.DYNAMIC,
                    onClick = { vm.launch { updateSettings { setThemeColorMode(ThemeColorMode.DYNAMIC) } } },
                    label = { Text("壁紙に合わせる") },
                )
                FilterChip(
                    selected = settings.themeColorMode == ThemeColorMode.MANUAL,
                    onClick = { vm.launch { updateSettings { setThemeColorMode(ThemeColorMode.MANUAL) } } },
                    label = { Text("自分で選ぶ") },
                )
            }
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
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text("明るさ", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DarkMode.SYSTEM to "端末に合わせる", DarkMode.LIGHT to "ライト", DarkMode.DARK to "ダーク").forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.darkMode == mode,
                        onClick = { vm.launch { updateSettings { setDarkMode(mode) } } },
                        label = { Text(label) },
                    )
                }
            }

            SectionHeader("ロック")
            val secure = remember { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }
            SwitchRow(
                title = "アプリを開くときにロックを解除する",
                note = if (secure) {
                    "端末の画面ロック(指紋・顔・PIN など)を使います。"
                } else {
                    "端末に画面ロックが設定されていないため、入にしてもロックはかかりません。"
                },
                checked = settings.appLockEnabled,
                onCheckedChange = { value -> vm.launch { updateSettings { setAppLockEnabled(value) } } },
            )

            SectionHeader("データ")
            ActionRow(
                title = "バックアップと復元",
                note = "記録は端末の中だけにあります。機種変更や故障に備えて、ときどき書き出してください。",
                action = "開く",
                onAction = onOpenBackup,
            )

            val archivedTitles = roster?.titles.orEmpty().filter { it.isArchived }
            val archivedAccounts = roster?.accounts.orEmpty().filter { a -> a.isArchived && roster?.titleById?.get(a.gameTitleId)?.isArchived == false }
            SectionHeader("アーカイブした物")
            if (archivedTitles.isEmpty() && archivedAccounts.isEmpty()) {
                Text("ありません。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            archivedTitles.forEach { title ->
                ArchivedRow(
                    name = title.name,
                    onOpen = { onOpenTitle(title.id) },
                    onRestore = { vm.launch { saveTitle(title.copy(isArchived = false)) } },
                )
            }
            archivedAccounts.forEach { account ->
                ArchivedRow(
                    name = "${roster?.titleById?.get(account.gameTitleId)?.name.orEmpty()} / ${account.label}",
                    onOpen = { onOpenTitle(account.gameTitleId) },
                    onRestore = { vm.launch { saveAccount(account.copy(isArchived = false)) } },
                )
            }

            SectionHeader("このアプリについて")
            Text(
                "AltRoster ${BackupManager.appVersionName(context)}\n" +
                    "通信は一切しません(インターネットの権限を持っていません)。ゲームのサーバーにはつながず、" +
                    "ログインや操作の自動化も行いません。記録は手で入力した物だけです。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
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

@Composable
fun SwitchRow(title: String, note: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun ActionRow(title: String, note: String?, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun ArchivedRow(name: String, onOpen: () -> Unit, onRestore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "開く", onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, modifier = Modifier.weight(1f))
        TextButton(onClick = onRestore) { Text("戻す") }
    }
}
