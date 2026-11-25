package io.github.takosasidev.altroster.core.model

import java.time.Instant
import java.time.LocalTime

enum class ThemeColorMode { DYNAMIC, MANUAL }

enum class DarkMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val digestNotificationEnabled: Boolean = true,
    /** "HH:mm"。 */
    val digestNotificationTime: String = "09:00",
    /** タスクがリセットされた時の通知。通知を増やしすぎないよう、既定では出さない。 */
    val resetNotificationEnabled: Boolean = false,
    val themeColorMode: ThemeColorMode = ThemeColorMode.DYNAMIC,
    val manualThemeColor: Int? = null,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val autoBackupEnabled: Boolean = false,
    val autoBackupIntervalDays: Int = 7,
    val autoBackupKeepCount: Int = 5,
    val lastAutoBackupAt: Instant? = null,
    val appLockEnabled: Boolean = false,
) {
    /** 読めない値が入っていても落とさず、既定の 9 時にする。 */
    val digestTime: LocalTime
        get() = parseTime(digestNotificationTime) ?: LocalTime.of(9, 0)

    companion object {
        fun parseTime(raw: String): LocalTime? = runCatching {
            val (h, m) = raw.trim().split(":").map { it.toInt() }
            LocalTime.of(h, m)
        }.getOrNull()

        // String.format は端末の言語で数字の字形が変わるので使わない
        fun formatTime(hour: Int, minute: Int): String =
            hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
    }
}
