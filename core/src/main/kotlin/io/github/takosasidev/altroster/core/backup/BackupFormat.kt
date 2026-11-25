package io.github.takosasidev.altroster.core.backup

import io.github.takosasidev.altroster.core.calc.ResetSpec
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.DarkMode
import io.github.takosasidev.altroster.core.model.GachaLog
import io.github.takosasidev.altroster.core.model.GameAccount
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.Roster
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.core.model.TaskCheckIn
import io.github.takosasidev.altroster.core.model.ThemeColorMode
import java.io.StringReader
import java.io.StringWriter
import java.time.Instant
import java.time.LocalDate
import java.util.Properties

/** バックアップの zip から読み取った中身。 */
data class BackupContent(
    val roster: Roster,
    val settings: AppSettings,
    val exportedAt: Instant?,
    val appVersionName: String?,
    /** 読めなかった・親が見つからなかったなどで読み飛ばした行の数。確認の画面に出す。 */
    val skippedRows: Int,
)

sealed interface BackupDecodeResult {
    data class Ok(val content: BackupContent) : BackupDecodeResult

    /** 取り込んではいけない中身。message はそのまま画面に出せる文。 */
    data class Incompatible(val message: String) : BackupDecodeResult
}

/**
 * バックアップの zip の中身の形式。
 *
 * JSON のライブラリは足さない。java.util.Properties と CSV で足り、どちらも人が開いて読める。
 *
 * ```
 * manifest.properties   schemaVersion / appVersionName / exportedAt / 各表の件数
 * titles.csv  accounts.csv  staminas.csv  events.csv  tasks.csv  checkins.csv  gacha.csv
 * settings.properties   設定(保存先のフォルダと最後に自動で保存した日時は入れない)
 * ```
 *
 * ログインの手がかりのメモ(accounts.csv の loginHintMemo)も入る。パスワードを付けない zip は
 * 暗号化されないので、メモにパスワードを書かないことが前提になる。
 */
object BackupFormat {
    const val ENTRY_MANIFEST = "manifest.properties"
    const val ENTRY_TITLES = "titles.csv"
    const val ENTRY_ACCOUNTS = "accounts.csv"
    const val ENTRY_STAMINAS = "staminas.csv"
    const val ENTRY_EVENTS = "events.csv"
    const val ENTRY_TASKS = "tasks.csv"
    const val ENTRY_CHECKINS = "checkins.csv"
    const val ENTRY_GACHA = "gacha.csv"
    const val ENTRY_SETTINGS = "settings.properties"

    /** zip に入れる順。読む時もこの名前だけを取り出す(ほかの名前のエントリは無視する)。 */
    val ENTRIES: List<String> = listOf(
        ENTRY_MANIFEST, ENTRY_TITLES, ENTRY_ACCOUNTS, ENTRY_STAMINAS, ENTRY_EVENTS,
        ENTRY_TASKS, ENTRY_CHECKINS, ENTRY_GACHA, ENTRY_SETTINGS,
    )

    const val KEY_SCHEMA_VERSION = "schemaVersion"
    const val KEY_APP_VERSION = "appVersionName"
    const val KEY_EXPORTED_AT = "exportedAt"

    private val TITLE_HEADER = listOf("id", "name", "iconKey", "colorArgb", "sortOrder", "isArchived", "tags")
    private val ACCOUNT_HEADER = listOf("id", "gameTitleId", "label", "loginHintMemo", "createdAt", "isArchived")
    private val STAMINA_HEADER = listOf(
        "id", "gameAccountId", "resourceName", "maxValue", "recoveryIntervalMinutes",
        "recordedValue", "recordedAt", "notifyOnFull", "notifyThresholdPercent",
    )
    private val EVENT_HEADER = listOf("id", "gameTitleId", "name", "endAt", "reminderBeforeHours", "isCompleted")
    private val TASK_HEADER = listOf("id", "gameAccountId", "label", "resetType", "resetTime", "sortOrder")
    private val CHECKIN_HEADER = listOf("id", "dailyTaskId", "periodKey", "isChecked", "checkedAt")
    private val GACHA_HEADER = listOf("id", "gameAccountId", "date", "amount", "currencyType", "memo", "createdAt")

    // ---- 書き出し ----------------------------------------------------------

    /** エントリの名前 → 中身の文字列。 */
    fun encode(
        roster: Roster,
        settings: AppSettings,
        schemaVersion: Int,
        appVersionName: String,
        exportedAt: Instant,
    ): Map<String, String> = linkedMapOf(
        ENTRY_MANIFEST to writeProperties(
            Properties().apply {
                setProperty(KEY_SCHEMA_VERSION, schemaVersion.toString())
                setProperty(KEY_APP_VERSION, appVersionName)
                setProperty(KEY_EXPORTED_AT, exportedAt.toString())
                setProperty("titleCount", roster.titles.size.toString())
                setProperty("accountCount", roster.accounts.size.toString())
                setProperty("gachaLogCount", roster.gachaLogs.size.toString())
            },
            "AltRoster backup manifest",
        ),
        ENTRY_TITLES to table(TITLE_HEADER, roster.titles) { t ->
            listOf(
                t.id.toString(), t.name, t.iconKey, t.colorArgb.toString(), t.sortOrder.toString(),
                t.isArchived.toString(), t.tags.joinToString(","),
            )
        },
        ENTRY_ACCOUNTS to table(ACCOUNT_HEADER, roster.accounts) { a ->
            listOf(
                a.id.toString(), a.gameTitleId.toString(), a.label, a.loginHintMemo,
                a.createdAt.toString(), a.isArchived.toString(),
            )
        },
        ENTRY_STAMINAS to table(STAMINA_HEADER, roster.staminas) { s ->
            listOf(
                s.id.toString(), s.gameAccountId.toString(), s.resourceName, s.maxValue.toString(),
                s.recoveryIntervalMinutes.toString(), s.recordedValue.toString(), s.recordedAt.toString(),
                s.notifyOnFull.toString(), s.notifyThresholdPercent?.toString(),
            )
        },
        ENTRY_EVENTS to table(EVENT_HEADER, roster.events) { e ->
            listOf(
                e.id.toString(), e.gameTitleId.toString(), e.name, e.endAt.toString(),
                e.reminderBeforeHours.toString(), e.isCompleted.toString(),
            )
        },
        ENTRY_TASKS to table(TASK_HEADER, roster.tasks) { t ->
            listOf(
                t.id.toString(), t.gameAccountId.toString(), t.label, t.resetType.name,
                t.resetTime, t.sortOrder.toString(),
            )
        },
        ENTRY_CHECKINS to table(CHECKIN_HEADER, roster.checkIns) { c ->
            listOf(
                c.id.toString(), c.dailyTaskId.toString(), c.periodKey,
                c.isChecked.toString(), c.checkedAt?.toString(),
            )
        },
        ENTRY_GACHA to table(GACHA_HEADER, roster.gachaLogs) { g ->
            listOf(
                g.id.toString(), g.gameAccountId.toString(), g.date.toString(), g.amount.toString(),
                g.currencyType, g.memo, g.createdAt.toString(),
            )
        },
        ENTRY_SETTINGS to settingsProperties(settings),
    )

    private fun <T> table(header: List<String>, rows: List<T>, cells: (T) -> List<String?>): String =
        buildString {
            append(Csv.row(header))
            rows.forEach { append(Csv.row(cells(it))) }
        }

    // ---- 検証 --------------------------------------------------------------

    /**
     * 版が違うバックアップは取り込まない(要件定義書のエッジケース)。
     * 問題が無ければ null、あれば画面に出す文を返す。
     */
    fun validateManifest(manifestText: String?, supportedSchemaVersion: Int): String? {
        if (manifestText == null) {
            return "バックアップに $ENTRY_MANIFEST が入っていません。" +
                "このアプリの書き出しで作った zip を選び直してください。"
        }
        val version = readProperties(manifestText).getProperty(KEY_SCHEMA_VERSION)?.trim()?.toIntOrNull()
            ?: return "バックアップの形式が正しくありません($KEY_SCHEMA_VERSION を読み取れません)。" +
                "このアプリの書き出しで作った zip を選び直してください。"
        return when {
            version == supportedSchemaVersion -> null
            version > supportedSchemaVersion ->
                "このバックアップは新しい版のアプリ(データの形式 v$version)で作られています。" +
                    "このアプリ(v$supportedSchemaVersion)では読み込めないため、取り込みを中止しました。" +
                    "アプリを新しい版にしてから試してください。"
            else ->
                "このバックアップは古い版のアプリ(データの形式 v$version)で作られています。" +
                    "このアプリ(v$supportedSchemaVersion)とは形式が違うため、取り込みを中止しました。"
        }
    }

    // ---- 読み取り ----------------------------------------------------------

    /**
     * zip から取り出した文字列を検証して、記録に戻す。
     *
     * 止めるのは、版が違う・表のファイルが無い・列が足りない、のとき。
     * 行の単位の傷(数字でない、親のタイトルやアカウントが無い、id が重なる)は、その行だけ読み飛ばして数える。
     * 親の無い行を入れると DB の外部キーで取り込み全体が失敗するので、ここで落としておく。
     */
    fun decode(texts: Map<String, String>, supportedSchemaVersion: Int): BackupDecodeResult {
        validateManifest(texts[ENTRY_MANIFEST], supportedSchemaVersion)
            ?.let { return BackupDecodeResult.Incompatible(it) }

        var skipped = 0

        // 表を 1 つ読む。必須の列が無ければ例外で抜けて、下の catch で Incompatible にする。
        fun <T> read(entry: String, header: List<String>, idOf: (T) -> Long, transform: (get: (String) -> String) -> T?): List<T> {
            val text = texts[entry] ?: throw BrokenBackup("バックアップに $entry が入っていません。取り込みを中止しました。")
            val rows = Csv.parse(text).filter { row -> row.any { it.isNotBlank() } }
            val names = rows.firstOrNull()?.map { it.trim() }
                ?: throw BrokenBackup("$entry が空です。取り込みを中止しました。")
            val missing = header.filter { it !in names }
            if (missing.isNotEmpty()) {
                throw BrokenBackup("$entry に列が足りません(${missing.joinToString("、")})。取り込みを中止しました。")
            }
            val parsed = rows.drop(1).mapNotNull { row ->
                val item = runCatching {
                    transform { key -> row.getOrNull(names.indexOf(key)).orEmpty() }
                }.getOrNull()
                if (item == null) skipped++
                item
            }
            val unique = parsed.distinctBy(idOf)
            skipped += parsed.size - unique.size
            return unique
        }

        return try {
            val titles = read<GameTitle>(ENTRY_TITLES, TITLE_HEADER, { it.id }) { get ->
                GameTitle(
                    id = positiveId(get("id")),
                    name = get("name").ifBlank { return@read null },
                    iconKey = get("iconKey").ifBlank { null },
                    colorArgb = get("colorArgb").toInt(),
                    sortOrder = get("sortOrder").toIntOrNull() ?: 0,
                    isArchived = get("isArchived").toBoolean(),
                    tags = GameTitle.normalizeTags(get("tags").split(",")),
                )
            }
            val titleIds = titles.map { it.id }.toSet()

            val accounts = read<GameAccount>(ENTRY_ACCOUNTS, ACCOUNT_HEADER, { it.id }) { get ->
                GameAccount(
                    id = positiveId(get("id")),
                    gameTitleId = get("gameTitleId").toLong().takeIf { it in titleIds } ?: return@read null,
                    label = get("label").ifBlank { return@read null },
                    loginHintMemo = get("loginHintMemo").ifBlank { null },
                    createdAt = Instant.parse(get("createdAt")),
                    isArchived = get("isArchived").toBoolean(),
                )
            }
            val accountIds = accounts.map { it.id }.toSet()

            val staminas = read<StaminaResource>(ENTRY_STAMINAS, STAMINA_HEADER, { it.id }) { get ->
                val max = get("maxValue").toInt().takeIf { it >= 1 } ?: return@read null
                StaminaResource(
                    id = positiveId(get("id")),
                    gameAccountId = get("gameAccountId").toLong().takeIf { it in accountIds } ?: return@read null,
                    resourceName = get("resourceName").ifBlank { return@read null },
                    maxValue = max,
                    recoveryIntervalMinutes = get("recoveryIntervalMinutes").toInt().takeIf { it >= 1 } ?: return@read null,
                    recordedValue = get("recordedValue").toInt().coerceIn(0, max),
                    recordedAt = Instant.parse(get("recordedAt")),
                    notifyOnFull = get("notifyOnFull").toBoolean(),
                    notifyThresholdPercent = get("notifyThresholdPercent").toIntOrNull()?.takeIf { it in 1..99 },
                )
            }

            val events = read<GameEvent>(ENTRY_EVENTS, EVENT_HEADER, { it.id }) { get ->
                GameEvent(
                    id = positiveId(get("id")),
                    gameTitleId = get("gameTitleId").toLong().takeIf { it in titleIds } ?: return@read null,
                    name = get("name").ifBlank { return@read null },
                    endAt = Instant.parse(get("endAt")),
                    reminderBeforeHours = get("reminderBeforeHours").toInt().coerceAtLeast(0),
                    isCompleted = get("isCompleted").toBoolean(),
                )
            }

            val tasks = read<DailyTask>(ENTRY_TASKS, TASK_HEADER, { it.id }) { get ->
                val type = ResetType.valueOf(get("resetType"))
                DailyTask(
                    id = positiveId(get("id")),
                    gameAccountId = get("gameAccountId").toLong().takeIf { it in accountIds } ?: return@read null,
                    label = get("label").ifBlank { return@read null },
                    resetType = type,
                    // 読めない時刻が入っていても、既定の時刻に直してから入れる
                    resetTime = ResetSpec.parse(type, get("resetTime")).encode(),
                    sortOrder = get("sortOrder").toIntOrNull() ?: 0,
                )
            }
            val taskIds = tasks.map { it.id }.toSet()

            val checkInsRaw = read<TaskCheckIn>(ENTRY_CHECKINS, CHECKIN_HEADER, { it.id }) { get ->
                TaskCheckIn(
                    id = positiveId(get("id")),
                    dailyTaskId = get("dailyTaskId").toLong().takeIf { it in taskIds } ?: return@read null,
                    periodKey = LocalDate.parse(get("periodKey")).toString(),
                    isChecked = get("isChecked").toBoolean(),
                    checkedAt = get("checkedAt").ifBlank { null }?.let(Instant::parse),
                )
            }
            // DB では(タスク, 期間)が一意。重なった行があると取り込み全体が失敗するので 1 つにする
            val checkIns = checkInsRaw.distinctBy { it.dailyTaskId to it.periodKey }
            skipped += checkInsRaw.size - checkIns.size

            val gachaLogs = read<GachaLog>(ENTRY_GACHA, GACHA_HEADER, { it.id }) { get ->
                GachaLog(
                    id = positiveId(get("id")),
                    gameAccountId = get("gameAccountId").toLong().takeIf { it in accountIds } ?: return@read null,
                    date = LocalDate.parse(get("date")),
                    amount = get("amount").toInt().takeIf { it >= 0 } ?: return@read null,
                    currencyType = get("currencyType").ifBlank { null },
                    memo = get("memo").ifBlank { null },
                    createdAt = Instant.parse(get("createdAt")),
                )
            }

            val manifest = readProperties(texts.getValue(ENTRY_MANIFEST))
            BackupDecodeResult.Ok(
                BackupContent(
                    roster = Roster(titles, accounts, staminas, events, tasks, checkIns, gachaLogs),
                    settings = parseSettings(texts[ENTRY_SETTINGS]),
                    exportedAt = manifest.getProperty(KEY_EXPORTED_AT)?.let { runCatching { Instant.parse(it) }.getOrNull() },
                    appVersionName = manifest.getProperty(KEY_APP_VERSION),
                    skippedRows = skipped,
                ),
            )
        } catch (e: BrokenBackup) {
            BackupDecodeResult.Incompatible(e.message.orEmpty())
        }
    }

    private class BrokenBackup(message: String) : Exception(message)

    private fun positiveId(raw: String): Long = raw.toLong().also { require(it > 0) }

    // ---- 設定 --------------------------------------------------------------

    fun settingsProperties(s: AppSettings): String = writeProperties(
        Properties().apply {
            setProperty("digestNotificationEnabled", s.digestNotificationEnabled.toString())
            setProperty("digestNotificationTime", s.digestNotificationTime)
            setProperty("resetNotificationEnabled", s.resetNotificationEnabled.toString())
            setProperty("themeColorMode", s.themeColorMode.name)
            s.manualThemeColor?.let { setProperty("manualThemeColor", it.toString()) }
            setProperty("darkMode", s.darkMode.name)
            setProperty("autoBackupEnabled", s.autoBackupEnabled.toString())
            setProperty("autoBackupIntervalDays", s.autoBackupIntervalDays.toString())
            setProperty("autoBackupKeepCount", s.autoBackupKeepCount.toString())
            setProperty("appLockEnabled", s.appLockEnabled.toString())
        },
        "AltRoster settings",
    )

    /** 無いキー・読めない値は既定の値にする。後からキーが増えても取り込みが失敗しないように。 */
    fun parseSettings(text: String?): AppSettings {
        val d = AppSettings()
        val p = readProperties(text ?: return d)
        fun str(key: String): String? = p.getProperty(key)?.trim()?.ifBlank { null }
        fun int(key: String, fallback: Int, min: Int): Int = str(key)?.toIntOrNull()?.takeIf { it >= min } ?: fallback
        fun bool(key: String, fallback: Boolean): Boolean = str(key)?.toBooleanStrictOrNull() ?: fallback
        return AppSettings(
            digestNotificationEnabled = bool("digestNotificationEnabled", d.digestNotificationEnabled),
            digestNotificationTime = str("digestNotificationTime")
                ?.takeIf { AppSettings.parseTime(it) != null } ?: d.digestNotificationTime,
            resetNotificationEnabled = bool("resetNotificationEnabled", d.resetNotificationEnabled),
            themeColorMode = str("themeColorMode")
                ?.let { v -> runCatching { ThemeColorMode.valueOf(v) }.getOrNull() } ?: d.themeColorMode,
            manualThemeColor = str("manualThemeColor")?.toIntOrNull(),
            darkMode = str("darkMode")?.let { v -> runCatching { DarkMode.valueOf(v) }.getOrNull() } ?: d.darkMode,
            autoBackupEnabled = bool("autoBackupEnabled", d.autoBackupEnabled),
            autoBackupIntervalDays = int("autoBackupIntervalDays", d.autoBackupIntervalDays, 1),
            autoBackupKeepCount = int("autoBackupKeepCount", d.autoBackupKeepCount, 1),
            appLockEnabled = bool("appLockEnabled", d.appLockEnabled),
        )
    }

    private fun writeProperties(props: Properties, comment: String): String =
        StringWriter().use { w ->
            props.store(w, comment)
            w.toString()
        }

    private fun readProperties(text: String): Properties =
        Properties().apply { StringReader(text).use { load(it) } }
}
