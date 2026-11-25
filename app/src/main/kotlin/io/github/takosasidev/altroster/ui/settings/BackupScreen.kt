@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.takosasidev.altroster.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.backup.AutoBackupRule
import io.github.takosasidev.altroster.core.backup.BackupContent
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.data.backup.BackupManager
import io.github.takosasidev.altroster.data.backup.BackupReadResult
import io.github.takosasidev.altroster.ui.RosterViewModel
import io.github.takosasidev.altroster.ui.common.ConfirmDialog
import io.github.takosasidev.altroster.ui.common.SectionHeader
import kotlinx.coroutines.launch

/**
 * バックアップの書き出し・取り込み・自動のバックアップの設定。
 * 保存先と読み込むファイルは、端末のファイルの選択の画面で利用者が選ぶ(ストレージの権限は要らない)。
 */
@Composable
fun BackupScreen(vm: RosterViewModel, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val store = vm.repo.settingsStore
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: AppSettings()
    val treeUri by store.backupTreeUri.collectAsStateWithLifecycle(initialValue = null)
    val autoError by store.lastAutoBackupError.collectAsStateWithLifecycle(initialValue = null)

    var exportPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 取り込みの途中の状態
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var importPassword by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<BackupContent?>(null) }

    fun say(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun read(uri: Uri, password: String?) {
        busy = true
        scope.launch {
            when (val result = BackupManager.readBackup(context, uri, password)) {
                is BackupReadResult.Ok -> {
                    askPassword = false
                    pending = result.content
                }
                BackupReadResult.NeedPassword -> {
                    passwordError = null
                    askPassword = true
                }
                is BackupReadResult.WrongPassword -> {
                    passwordError = result.message
                    askPassword = true
                }
                is BackupReadResult.Incompatible -> {
                    askPassword = false
                    say(result.message)
                }
                is BackupReadResult.Error -> {
                    askPassword = false
                    say(result.message)
                }
            }
            busy = false
        }
    }

    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.ZIP_MIME)) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching { BackupManager.exportToUri(context, uri, exportPassword.ifEmpty { null }) }
                    .onSuccess { say(if (exportPassword.isEmpty()) "書き出しました。" else "パスワード付きで書き出しました。") }
                    .onFailure { say("書き出せませんでした(${it.message ?: it.javaClass.simpleName})。") }
                busy = false
            }
        }
    }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importUri = uri
            importPassword = ""
            read(uri, null)
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (BackupManager.persistTree(context, uri)) {
                scope.launch {
                    store.setBackupTreeUri(uri.toString())
                    store.setLastAutoBackupError(null)
                }
            } else {
                say("このフォルダは保存先に使えませんでした。別のフォルダを選んでください。")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("バックアップと復元") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("書き出し")
            Text(
                "全部の記録と設定を 1 つの zip にします。ログインの手がかりのメモも入ります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = exportPassword,
                onValueChange = { exportPassword = it.take(64) },
                label = { Text("パスワード(任意)") },
                supportingText = { Text("入れると AES-256 で暗号化します。忘れると復元できません。空なら暗号化しません。") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy,
                onClick = { createZip.launch(AutoBackupRule.fileName(AutoBackupRule.MANUAL_PREFIX, vm.now(), vm.zone())) },
            ) { Text("書き出す") }

            SectionHeader("復元(取り込み)")
            Text(
                "書き出した zip を選ぶと、今の記録を全部置き換えます。置き換える前に、中身の件数を確かめる画面が出ます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(enabled = !busy, onClick = { openZip.launch(arrayOf(BackupManager.ZIP_MIME, "application/octet-stream")) }) {
                Text("zip を選ぶ")
            }

            SectionHeader("自動のバックアップ")
            SwitchRow(
                title = "アプリを開いたときに自動で書き出す",
                note = "決めた日数がたっていたら、開いたときに 1 回だけ、下のフォルダへ書き出します(パスワードは付きません)。",
                checked = settings.autoBackupEnabled,
                onCheckedChange = { value -> vm.launch { updateSettings { setAutoBackupEnabled(value) } } },
            )
            val folderName = treeUri?.let { BackupManager.treeDisplayName(context, Uri.parse(it)) }
            ActionRow(
                title = "保存先: ${folderName ?: if (treeUri == null) "まだ選んでいません" else "読めません(選び直してください)"}",
                note = null,
                action = "選ぶ",
                onAction = { pickFolder.launch(null) },
            )
            Text("間隔", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 7, 14, 30).forEach { days ->
                    FilterChip(
                        selected = settings.autoBackupIntervalDays == days,
                        onClick = { vm.launch { updateSettings { setAutoBackupIntervalDays(days) } } },
                        label = { Text("${days}日") },
                    )
                }
            }
            Text("残す数(これを超えた古い自動の分は消します)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10, 20).forEach { count ->
                    FilterChip(
                        selected = settings.autoBackupKeepCount == count,
                        onClick = { vm.launch { updateSettings { setAutoBackupKeepCount(count) } } },
                        label = { Text("${count}個") },
                    )
                }
            }
            Text(
                "最後に自動で書き出した日時: " + (settings.lastAutoBackupAt?.let { Format.dateTime(it, vm.zone()) } ?: "まだありません"),
                style = MaterialTheme.typography.bodySmall,
            )
            autoError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Text("", modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (askPassword) {
        AlertDialog(
            onDismissRequest = { askPassword = false },
            title = { Text("パスワードを入れてください") },
            text = {
                OutlinedTextField(
                    value = importPassword,
                    onValueChange = { importPassword = it },
                    label = { Text("パスワード") },
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = importPassword.isNotEmpty() && !busy,
                    onClick = { importUri?.let { read(it, importPassword) } },
                ) { Text("読み込む") }
            },
            dismissButton = { TextButton(onClick = { askPassword = false }) { Text("やめる") } },
        )
    }

    pending?.let { content ->
        val r = content.roster
        val skipped = if (content.skippedRows > 0) "\n読めなかった行が ${content.skippedRows} 行あり、その行は取り込みません。" else ""
        ConfirmDialog(
            title = "今の記録を置き換えますか",
            text = "バックアップの中身: タイトル ${r.titles.size} 件、アカウント ${r.accounts.size} 件、スタミナ ${r.staminas.size} 件、" +
                "イベント ${r.events.size} 件、タスク ${r.tasks.size} 件、課金の記録 ${r.gachaLogs.size} 件" +
                (content.exportedAt?.let { "(${Format.dateTime(it, vm.zone())} に書き出し)" } ?: "") +
                "。$skipped\n今この端末にある記録は全部消えて、この中身に置き換わります。取り消せません。",
            confirmLabel = "置き換える",
            onConfirm = {
                pending = null
                busy = true
                scope.launch {
                    runCatching { BackupManager.restore(context, content) }
                        .onSuccess { say("復元しました。") }
                        .onFailure { say("復元できませんでした(${it.message ?: it.javaClass.simpleName})。今の記録はそのままです。") }
                    busy = false
                }
            },
            onDismiss = { pending = null },
        )
    }
}
