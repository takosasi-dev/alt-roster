package io.github.takosasidev.altroster.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import io.github.takosasidev.altroster.core.backup.AutoBackupRule
import io.github.takosasidev.altroster.core.backup.BackupContent
import io.github.takosasidev.altroster.core.backup.BackupDecodeResult
import io.github.takosasidev.altroster.core.backup.BackupFormat
import io.github.takosasidev.altroster.data.RosterRepository
import io.github.takosasidev.altroster.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/** 取り込みの読み取りの結果。パスワードの違いと、形式の違いを区別して伝える。 */
sealed interface BackupReadResult {
    data class Ok(val content: BackupContent) : BackupReadResult

    /** 暗号化された zip だが、パスワードが渡されていない。 */
    data object NeedPassword : BackupReadResult
    data class WrongPassword(val message: String) : BackupReadResult

    /** 版が違うなど、取り込んではいけない中身。 */
    data class Incompatible(val message: String) : BackupReadResult
    data class Error(val message: String) : BackupReadResult
}

/**
 * バックアップの zip の読み書き。中身の形式と検証は :core の BackupFormat にある。
 *
 * zip は Zip4j で読み書きする(パスワードの有り無しで処理を分けないため)。Zip4j は File を求めるので、
 * 利用者が選んだ保存先(Uri)とは、アプリのキャッシュの一時ファイルを挟んでやり取りする。
 */
object BackupManager {
    const val ZIP_MIME = "application/zip"

    // ---- 書き出し ----------------------------------------------------------

    /** 今の記録と設定を zip にして [uri] へ書く。[password] が空でなければ AES-256 で暗号化する。 */
    suspend fun exportToUri(context: Context, uri: Uri, password: String?) = withContext(Dispatchers.IO) {
        val repo = RosterRepository.get(context)
        val texts = BackupFormat.encode(
            roster = repo.rosterOnce(),
            settings = repo.settingsStore.current(),
            schemaVersion = AppDatabase.VERSION,
            appVersionName = appVersionName(context),
            exportedAt = repo.now(),
        )
        val temp = File.createTempFile("export", AutoBackupRule.ZIP_EXT, context.cacheDir)
        try {
            zipTo(temp, texts, password)
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("保存先を開けませんでした")
            out.use { target -> temp.inputStream().use { it.copyTo(target) } }
        } finally {
            temp.delete()
        }
    }

    private fun zipTo(target: File, texts: Map<String, String>, password: String?) {
        target.delete() // Zip4j は既にあるファイルに追記するので、必ず作り直す
        val encrypted = !password.isNullOrEmpty()
        val zip = if (encrypted) ZipFile(target, password!!.toCharArray()) else ZipFile(target)
        zip.use { z ->
            texts.forEach { (name, text) ->
                // 暗号化の指定はエントリごと。全部のエントリに同じ指定を付ける
                val params = ZipParameters().apply {
                    fileNameInZip = name
                    if (encrypted) {
                        isEncryptFiles = true
                        encryptionMethod = EncryptionMethod.AES
                        aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }
                }
                ByteArrayInputStream(text.toByteArray()).use { z.addStream(it, params) }
            }
        }
    }

    // ---- 読み取り ----------------------------------------------------------

    suspend fun readBackup(context: Context, uri: Uri, password: String?): BackupReadResult =
        withContext(Dispatchers.IO) {
            val temp = File.createTempFile("import", AutoBackupRule.ZIP_EXT, context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext BackupReadResult.Error("ファイルを開けませんでした。選び直してください。")
                input.use { src -> FileOutputStream(temp).use { src.copyTo(it) } }
                readBackupFile(temp, password)
            } catch (e: SecurityException) {
                BackupReadResult.Error("ファイルを読む許可がありません。もう一度ファイルを選び直してください。")
            } catch (e: Exception) {
                BackupReadResult.Error("ファイルを読み込めませんでした(${reason(e)})。")
            } finally {
                temp.delete()
            }
        }

    fun readBackupFile(file: File, password: String?): BackupReadResult {
        val encrypted = try {
            ZipFile(file).use { it.isEncrypted }
        } catch (e: Exception) {
            return BackupReadResult.Error(
                "バックアップの zip として読み取れませんでした。このアプリの書き出しで作った zip を選び直してください。",
            )
        }
        if (encrypted && password.isNullOrEmpty()) return BackupReadResult.NeedPassword

        val texts: Map<String, String> = try {
            val zip = if (password.isNullOrEmpty()) ZipFile(file) else ZipFile(file, password.toCharArray())
            zip.use { z ->
                // 決まった名前のエントリだけを、メモリの上の文字列として読む。
                // zip の中のパスを使ってファイルを書くことは無いので、外から来た zip に
                // 変な名前のエントリがあっても、アプリの外や中のファイルを書き換えられない。
                BackupFormat.ENTRIES.mapNotNull { name ->
                    val header = z.getFileHeader(name) ?: return@mapNotNull null
                    name to z.getInputStream(header).use { s -> s.readBytes().toString(Charsets.UTF_8) }
                }.toMap()
            }
        } catch (e: ZipException) {
            // AES はパスワードの検証で止まる。違うパスワードのまま読み進めて、壊れた記録を入れない
            return if (encrypted) {
                BackupReadResult.WrongPassword("パスワードが違います。もう一度入力してください。")
            } else {
                BackupReadResult.Error("バックアップの中身を読み取れませんでした(${reason(e)})。")
            }
        } catch (e: Exception) {
            return BackupReadResult.Error("バックアップの中身を読み取れませんでした(${reason(e)})。")
        }

        return when (val decoded = BackupFormat.decode(texts, AppDatabase.VERSION)) {
            is BackupDecodeResult.Ok -> BackupReadResult.Ok(decoded.content)
            is BackupDecodeResult.Incompatible -> BackupReadResult.Incompatible(decoded.message)
        }
    }

    /** 今の記録を全部置き換えて取り込む。確認は呼ぶ側の画面が行う。 */
    suspend fun restore(context: Context, content: BackupContent) = withContext(Dispatchers.IO) {
        RosterRepository.get(context).replaceAll(content.roster, content.settings)
    }

    // ---- 自動のバックアップの保存先のフォルダ -------------------------------
    // androidx.documentfile を足さずに済ませるため、DocumentsContract を直接使う。

    /** フォルダを選んだ結果を覚えておく。以後は選び直さなくても書き込める。 */
    fun persistTree(context: Context, treeUri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    }.getOrDefault(false)

    /** 許可が生きていて、実際に中を読めるか。フォルダを動かされると、許可は残ったまま読めなくなる。 */
    fun hasTreeAccess(context: Context, treeUri: Uri): Boolean {
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }
        return granted && runCatching { listChildren(context, treeUri) }.isSuccess
    }

    fun treeDisplayName(context: Context, treeUri: Uri): String? = runCatching {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /** 覚えてあるフォルダに zip を作って書き出す。失敗は例外で上に投げる(黙って失敗しない)。 */
    suspend fun exportToTree(context: Context, treeUri: Uri, fileName: String, password: String?): Uri =
        withContext(Dispatchers.IO) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val target = DocumentsContract.createDocument(context.contentResolver, parent, ZIP_MIME, fileName)
                ?: throw IllegalStateException("保存先のフォルダにファイルを作れませんでした")
            exportToUri(context, target, password)
            target
        }

    /** フォルダの中のファイルの (名前, Uri)。読めなければ例外。 */
    private fun listChildren(context: Context, treeUri: Uri): List<Pair<String, Uri>> {
        val cursor = context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        ) ?: throw IllegalStateException("フォルダを読めませんでした")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    add(name to DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                }
            }
        }
    }

    /** 残す数を超えた古い自動のバックアップを消す。どれを消すかは :core の AutoBackupRule が決める。 */
    fun pruneAutoBackups(context: Context, treeUri: Uri, keepCount: Int) {
        val children = runCatching { listChildren(context, treeUri) }.getOrDefault(emptyList())
        val victims = AutoBackupRule.toPrune(children.map { it.first }, keepCount).toSet()
        children.filter { it.first in victims }.forEach { (_, uri) ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
    }

    fun appVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    private fun reason(e: Throwable): String = e.message ?: e.javaClass.simpleName
}
