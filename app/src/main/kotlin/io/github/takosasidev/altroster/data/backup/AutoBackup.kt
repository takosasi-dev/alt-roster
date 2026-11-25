package io.github.takosasidev.altroster.data.backup

import android.content.Context
import android.net.Uri
import io.github.takosasidev.altroster.core.backup.AutoBackupRule
import io.github.takosasidev.altroster.data.RosterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 自動のバックアップ。アプリを開いたときに 1 回だけ確かめる方式(裏で定期的に動く仕組みは使わない)。
 * 失敗は黙って捨てず、理由を設定に残して、バックアップの画面に出す。
 * 自動の物にはパスワードを付けない(パスワードを端末に保存しないため)。
 */
object AutoBackup {
    const val ERR_NO_FOLDER = "自動バックアップに失敗しました。保存先のフォルダが決まっていません。フォルダを選んでください。"
    const val ERR_NO_ACCESS =
        "自動バックアップに失敗しました。保存先のフォルダを読み書きできません(動かしたか消した可能性があります)。" +
            "フォルダを選び直してください。"

    suspend fun runIfDue(context: Context) = withContext(Dispatchers.IO) {
        val repo = RosterRepository.get(context)
        val store = repo.settingsStore
        val settings = store.current()
        val now = repo.now()
        if (!AutoBackupRule.isDue(settings.autoBackupEnabled, settings.lastAutoBackupAt, settings.autoBackupIntervalDays, now)) {
            return@withContext
        }

        val treeUri = store.backupTreeUri.first()?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (treeUri == null) {
            store.setLastAutoBackupError(ERR_NO_FOLDER)
            return@withContext
        }
        if (!BackupManager.hasTreeAccess(context, treeUri)) {
            store.setLastAutoBackupError(ERR_NO_ACCESS)
            return@withContext
        }

        try {
            val name = AutoBackupRule.fileName(AutoBackupRule.AUTO_PREFIX, now, repo.zone())
            BackupManager.exportToTree(context, treeUri, name, password = null)
            // 間隔を何回分も過ぎていても、次の基準は今の時刻。開くたびに何本も取り続けない
            store.setLastAutoBackupAt(now)
            BackupManager.pruneAutoBackups(context, treeUri, settings.autoBackupKeepCount)
            store.setLastAutoBackupError(null)
        } catch (e: Exception) {
            store.setLastAutoBackupError(
                "自動バックアップに失敗しました(${e.message ?: e.javaClass.simpleName})。保存先を確かめてください。",
            )
        }
    }
}
