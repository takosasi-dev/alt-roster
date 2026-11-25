package io.github.takosasidev.altroster.core

import io.github.takosasidev.altroster.core.backup.AutoBackupRule
import io.github.takosasidev.altroster.core.backup.BackupDecodeResult
import io.github.takosasidev.altroster.core.backup.BackupFormat
import io.github.takosasidev.altroster.core.backup.Csv
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DarkMode
import io.github.takosasidev.altroster.core.model.Roster
import io.github.takosasidev.altroster.core.model.ThemeColorMode
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupFormatTest {
    private val exportedAt = jst(2026, 8, 25, 14, 0)

    private val roster = Roster(
        titles = listOf(title(1, "タイトルA", tags = listOf("RPG", "毎日")), title(2, "タイトル,B\n改行", archived = true)),
        accounts = listOf(
            account(1, 1).copy(loginHintMemo = "連携: メールのほう\n\"引用符\" と , カンマ"),
            account(2, 2, label = "サブ", archived = true),
        ),
        staminas = listOf(stamina(1, 1, threshold = 90), stamina(2, 2, notifyOnFull = false)),
        events = listOf(event(1, 1, endAt = jst(2026, 9, 1, 12, 0), hours = 6, completed = true)),
        tasks = listOf(daily(1, 1), weekly(2, 2, time = "4 04:30")),
        checkIns = listOf(checkIn(1, 1, "2026-08-25", at = jst(2026, 8, 25, 9, 0)), checkIn(2, 2, "2026-08-20", checked = false)),
        gachaLogs = listOf(gacha(1, 1, LocalDate.of(2026, 8, 1), 3000, memo = "10 連、当たり無し")),
    )

    private val settings = AppSettings(
        digestNotificationEnabled = false,
        digestNotificationTime = "21:30",
        resetNotificationEnabled = true,
        themeColorMode = ThemeColorMode.MANUAL,
        manualThemeColor = 0xFF00796B.toInt(),
        darkMode = DarkMode.DARK,
        autoBackupEnabled = true,
        autoBackupIntervalDays = 3,
        autoBackupKeepCount = 9,
        appLockEnabled = true,
    )

    private fun encode(r: Roster = roster, version: Int = 1) =
        BackupFormat.encode(r, settings, schemaVersion = version, appVersionName = "0.1.0", exportedAt = exportedAt)

    private fun ok(texts: Map<String, String>) =
        assertIs<BackupDecodeResult.Ok>(BackupFormat.decode(texts, supportedSchemaVersion = 1)).content

    private fun rejected(texts: Map<String, String>) =
        assertIs<BackupDecodeResult.Incompatible>(BackupFormat.decode(texts, supportedSchemaVersion = 1)).message

    @Test
    fun 書き出して読み戻すと_同じ中身に戻る() {
        val content = ok(encode())
        assertEquals(roster, content.roster)
        assertEquals(settings, content.settings)
        assertEquals(exportedAt, content.exportedAt)
        assertEquals("0.1.0", content.appVersionName)
        assertEquals(0, content.skippedRows)
    }

    @Test
    fun 入れるファイルは決まった9つ() {
        assertEquals(BackupFormat.ENTRIES, encode().keys.toList())
    }

    @Test
    fun 最後に自動で保存した日時は_バックアップに入れない() {
        val text = BackupFormat.settingsProperties(settings.copy(lastAutoBackupAt = exportedAt))
        assertFalse(text.contains("lastAutoBackupAt"))
        assertNull(BackupFormat.parseSettings(text).lastAutoBackupAt)
    }

    @Test
    fun 版が違うバックアップは取り込まない() {
        assertTrue(rejected(encode(version = 2)).contains("新しい版"))
        assertTrue(rejected(encode(version = 0)).contains("古い版"))
    }

    @Test
    fun manifest_が無い_読めないバックアップは取り込まない() {
        assertTrue(rejected(encode() - BackupFormat.ENTRY_MANIFEST).contains(BackupFormat.ENTRY_MANIFEST))
        assertTrue(rejected(encode() + (BackupFormat.ENTRY_MANIFEST to "schemaVersion=abc")).contains("形式が正しくありません"))
    }

    @Test
    fun 表のファイルが欠けていたら取り込まない() {
        assertTrue(rejected(encode() - BackupFormat.ENTRY_GACHA).contains(BackupFormat.ENTRY_GACHA))
        assertTrue(rejected(encode() + (BackupFormat.ENTRY_TITLES to "")).contains("空です"))
    }

    @Test
    fun 列が足りない表は取り込まない() {
        val broken = encode() + (BackupFormat.ENTRY_TITLES to "id,name\r\n1,タイトルA\r\n")
        assertTrue(rejected(broken).contains("列が足りません"))
    }

    @Test
    fun 設定のファイルが無くても_既定の設定で取り込む() {
        assertEquals(AppSettings(), ok(encode() - BackupFormat.ENTRY_SETTINGS).settings)
    }

    @Test
    fun 親の無い行と読めない行は_その行だけ読み飛ばして数える() {
        val texts = encode().toMutableMap()
        // 無いタイトル(99)のアカウントと、その下のスタミナ。数字でない金額。日付でない期間
        texts[BackupFormat.ENTRY_ACCOUNTS] = texts.getValue(BackupFormat.ENTRY_ACCOUNTS) +
            "50,99,迷子,,2026-01-01T00:00:00Z,false\r\n"
        texts[BackupFormat.ENTRY_STAMINAS] = texts.getValue(BackupFormat.ENTRY_STAMINAS) +
            "51,50,AP,100,5,10,2026-01-01T00:00:00Z,true,\r\n"
        texts[BackupFormat.ENTRY_GACHA] = texts.getValue(BackupFormat.ENTRY_GACHA) +
            "52,1,2026-08-02,たくさん,,,2026-01-01T00:00:00Z\r\n" +
            "53,1,2026-08-03,-500,,,2026-01-01T00:00:00Z\r\n"
        texts[BackupFormat.ENTRY_CHECKINS] = texts.getValue(BackupFormat.ENTRY_CHECKINS) +
            "54,1,きのう,true,\r\n"

        val content = ok(texts)
        assertEquals(roster, content.roster)
        assertEquals(5, content.skippedRows)
    }

    @Test
    fun 同じidの行と_同じタスクと期間のチェックは_1つだけ残す() {
        val texts = encode().toMutableMap()
        texts[BackupFormat.ENTRY_TITLES] = texts.getValue(BackupFormat.ENTRY_TITLES) + "1,にせもの,,0,0,false,\r\n"
        texts[BackupFormat.ENTRY_CHECKINS] = texts.getValue(BackupFormat.ENTRY_CHECKINS) + "60,1,2026-08-25,true,\r\n"
        val content = ok(texts)
        assertEquals(roster, content.roster)
        assertEquals(2, content.skippedRows)
    }

    @Test
    fun 範囲の外の値は_安全な値に直すか読み飛ばす() {
        val texts = encode(Roster(titles = listOf(title(1)), accounts = listOf(account(1, 1)))).toMutableMap()
        texts[BackupFormat.ENTRY_STAMINAS] = texts.getValue(BackupFormat.ENTRY_STAMINAS) +
            "1,1,AP,100,5,999,2026-01-01T00:00:00Z,true,150\r\n" + // 値は最大に、割合は無しに直す
            "2,1,BP,0,5,0,2026-01-01T00:00:00Z,true,\r\n" + // 最大 0 は読み飛ばす
            "3,1,CP,10,0,0,2026-01-01T00:00:00Z,true,\r\n" // 間隔 0 は読み飛ばす
        texts[BackupFormat.ENTRY_TASKS] = texts.getValue(BackupFormat.ENTRY_TASKS) + "1,1,デイリー,DAILY,25:99,0\r\n"
        val content = ok(texts)
        val s = content.roster.staminas.single()
        assertEquals(100, s.recordedValue)
        assertNull(s.notifyThresholdPercent)
        assertEquals("05:00", content.roster.tasks.single().resetTime)
        assertEquals(2, content.skippedRows)
    }

    @Test
    fun CSV_はカンマ_引用符_改行を含む欄を往復できる() {
        val fields = listOf("ふつう", "a,b", "言った \"はい\"", "1 行目\n2 行目", " 前後の空白 ", "", null)
        val parsed = Csv.parse(Csv.row(fields) + Csv.row(listOf("次の行")))
        assertEquals(listOf(fields.map { it.orEmpty() }, listOf("次の行")), parsed)
    }

    @Test
    fun CSV_は_CRLF_でも_LF_でも_最後の改行が無くても読める() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), Csv.parse("a,b\nc,d"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), Csv.parse("a,b\r\nc,d\r\n"))
        assertTrue(Csv.parse("").isEmpty())
    }

    // ---- 自動のバックアップ --------------------------------------------------

    @Test
    fun 自動のバックアップは_切なら取らない_初回は取る_間隔が過ぎたら取る() {
        val last = jst(2026, 8, 18, 14, 0)
        assertFalse(AutoBackupRule.isDue(enabled = false, last = null, intervalDays = 7, now = exportedAt))
        assertTrue(AutoBackupRule.isDue(enabled = true, last = null, intervalDays = 7, now = exportedAt))
        assertTrue(AutoBackupRule.isDue(enabled = true, last = last, intervalDays = 7, now = exportedAt))
        assertFalse(AutoBackupRule.isDue(enabled = true, last = last.plusSeconds(1), intervalDays = 7, now = exportedAt))
        // 間隔が 0 以下でも、1 日として扱う(開くたびに取り続けない)
        assertFalse(AutoBackupRule.isDue(enabled = true, last = exportedAt.minusSeconds(3600), intervalDays = 0, now = exportedAt))
    }

    @Test
    fun 残す数を超えた古い自動のバックアップだけを消す() {
        val names = listOf(
            "altroster-auto-20260801-0900.zip",
            "altroster-auto-20260815-0900.zip",
            "altroster-auto-20260808-0900.zip",
            "altroster-backup-20260101-0900.zip",
            "写真.jpg",
            "altroster-auto-20260822-0900.zip",
        )
        assertEquals(
            listOf("altroster-auto-20260808-0900.zip", "altroster-auto-20260801-0900.zip"),
            AutoBackupRule.toPrune(names, keepCount = 2),
        )
        assertTrue(AutoBackupRule.toPrune(names, keepCount = 4).isEmpty())
        // 残す数が 0 以下でも、いちばん新しい 1 つは残す
        assertEquals(3, AutoBackupRule.toPrune(names, keepCount = 0).size)
    }

    @Test
    fun ファイルの名前には日時を入れる() {
        assertEquals(
            "altroster-auto-20260825-1400.zip",
            AutoBackupRule.fileName(AutoBackupRule.AUTO_PREFIX, exportedAt, JST),
        )
    }
}
