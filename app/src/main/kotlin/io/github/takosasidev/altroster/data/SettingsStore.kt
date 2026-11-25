package io.github.takosasidev.altroster.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DarkMode
import io.github.takosasidev.altroster.core.model.ThemeColorMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

/**
 * 設定は Room ではなく DataStore Preferences に置く。
 * 1 行だけの表のために Room の移行を背負わずに済み、キーが増えても既定の値に落ちるだけで済む。
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val digestEnabled = booleanPreferencesKey("digestNotificationEnabled")
        val digestTime = stringPreferencesKey("digestNotificationTime")
        val resetNotifyEnabled = booleanPreferencesKey("resetNotificationEnabled")
        val themeColorMode = stringPreferencesKey("themeColorMode")
        val manualThemeColor = intPreferencesKey("manualThemeColor")
        val darkMode = stringPreferencesKey("darkMode")
        val autoBackupEnabled = booleanPreferencesKey("autoBackupEnabled")
        val autoBackupIntervalDays = intPreferencesKey("autoBackupIntervalDays")
        val autoBackupKeepCount = intPreferencesKey("autoBackupKeepCount")
        val lastAutoBackupAt = longPreferencesKey("lastAutoBackupAt")
        val appLockEnabled = booleanPreferencesKey("appLockEnabled")
        val backupTreeUri = stringPreferencesKey("backupTreeUri")
        val lastAutoBackupError = stringPreferencesKey("lastAutoBackupError")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            digestNotificationEnabled = p[Keys.digestEnabled] ?: d.digestNotificationEnabled,
            digestNotificationTime = p[Keys.digestTime] ?: d.digestNotificationTime,
            resetNotificationEnabled = p[Keys.resetNotifyEnabled] ?: d.resetNotificationEnabled,
            themeColorMode = p[Keys.themeColorMode]
                ?.let { v -> runCatching { ThemeColorMode.valueOf(v) }.getOrNull() } ?: d.themeColorMode,
            manualThemeColor = p[Keys.manualThemeColor],
            darkMode = p[Keys.darkMode]?.let { v -> runCatching { DarkMode.valueOf(v) }.getOrNull() } ?: d.darkMode,
            autoBackupEnabled = p[Keys.autoBackupEnabled] ?: d.autoBackupEnabled,
            autoBackupIntervalDays = p[Keys.autoBackupIntervalDays] ?: d.autoBackupIntervalDays,
            autoBackupKeepCount = p[Keys.autoBackupKeepCount] ?: d.autoBackupKeepCount,
            lastAutoBackupAt = p[Keys.lastAutoBackupAt]?.let(Instant::ofEpochMilli),
            appLockEnabled = p[Keys.appLockEnabled] ?: d.appLockEnabled,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** 自動のバックアップの保存先のフォルダ。端末ごとの物なので、バックアップには入れない。 */
    val backupTreeUri: Flow<String?> = context.dataStore.data.map { it[Keys.backupTreeUri] }

    /** 自動のバックアップが最後に失敗した理由。成功したら消す。 */
    val lastAutoBackupError: Flow<String?> = context.dataStore.data.map { it[Keys.lastAutoBackupError] }

    suspend fun setDigestEnabled(value: Boolean) = put { it[Keys.digestEnabled] = value }
    suspend fun setDigestTime(value: String) = put { it[Keys.digestTime] = value }
    suspend fun setResetNotificationEnabled(value: Boolean) = put { it[Keys.resetNotifyEnabled] = value }
    suspend fun setThemeColorMode(value: ThemeColorMode) = put { it[Keys.themeColorMode] = value.name }
    suspend fun setManualThemeColor(argb: Int) = put { it[Keys.manualThemeColor] = argb }
    suspend fun setDarkMode(value: DarkMode) = put { it[Keys.darkMode] = value.name }
    suspend fun setAutoBackupEnabled(value: Boolean) = put { it[Keys.autoBackupEnabled] = value }
    suspend fun setAutoBackupIntervalDays(value: Int) = put { it[Keys.autoBackupIntervalDays] = value }
    suspend fun setAutoBackupKeepCount(value: Int) = put { it[Keys.autoBackupKeepCount] = value }
    suspend fun setAppLockEnabled(value: Boolean) = put { it[Keys.appLockEnabled] = value }
    suspend fun setLastAutoBackupAt(value: Instant) = put { it[Keys.lastAutoBackupAt] = value.toEpochMilli() }

    suspend fun setBackupTreeUri(value: String?) = put {
        if (value == null) it.remove(Keys.backupTreeUri) else it[Keys.backupTreeUri] = value
    }

    suspend fun setLastAutoBackupError(value: String?) = put {
        if (value == null) it.remove(Keys.lastAutoBackupError) else it[Keys.lastAutoBackupError] = value
    }

    /**
     * バックアップの取り込み用。設定を丸ごと書き戻す。
     * 保存先のフォルダと最後に自動で保存した日時は、この端末の物を残す。
     */
    suspend fun replaceAll(s: AppSettings) = put { p ->
        p[Keys.digestEnabled] = s.digestNotificationEnabled
        p[Keys.digestTime] = s.digestNotificationTime
        p[Keys.resetNotifyEnabled] = s.resetNotificationEnabled
        p[Keys.themeColorMode] = s.themeColorMode.name
        s.manualThemeColor?.let { p[Keys.manualThemeColor] = it } ?: p.remove(Keys.manualThemeColor)
        p[Keys.darkMode] = s.darkMode.name
        p[Keys.autoBackupEnabled] = s.autoBackupEnabled
        p[Keys.autoBackupIntervalDays] = s.autoBackupIntervalDays
        p[Keys.autoBackupKeepCount] = s.autoBackupKeepCount
        p[Keys.appLockEnabled] = s.appLockEnabled
    }

    private suspend fun put(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
