package io.github.takosasidev.altroster.core.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 自動のバックアップ(アプリを開いた時に確かめる方式)の判定。ファイルの操作は :app が行う。 */
object AutoBackupRule {
    const val MANUAL_PREFIX = "altroster-backup-"
    const val AUTO_PREFIX = "altroster-auto-"
    const val ZIP_EXT = ".zip"

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /** 名前に日時を入れておくと、名前の降順がそのまま新しい順になる。 */
    fun fileName(prefix: String, at: Instant, zone: ZoneId): String =
        prefix + STAMP.format(at.atZone(zone)) + ZIP_EXT

    /** 一度も取っていないか、前回から [intervalDays] 日たっていれば取る。 */
    fun isDue(enabled: Boolean, last: Instant?, intervalDays: Int, now: Instant): Boolean {
        if (!enabled) return false
        if (last == null) return true
        return !now.isBefore(last.plus(intervalDays.coerceAtLeast(1).toLong(), ChronoUnit.DAYS))
    }

    /**
     * 残す数を超えた自動のバックアップの名前(古い物)。
     * 手動で書き出した物と、関係のないファイルには触らない。
     */
    fun toPrune(names: List<String>, keepCount: Int): List<String> =
        names.filter { it.startsWith(AUTO_PREFIX) && it.endsWith(ZIP_EXT) }
            .sortedDescending()
            .drop(keepCount.coerceAtLeast(1))
}
