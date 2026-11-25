package io.github.takosasidev.altroster.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.takosasidev.altroster.core.calc.NoticePlan
import io.github.takosasidev.altroster.data.RosterRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * 通知のためのアラームの仕込み。
 *
 * 仕込むのは「次に何かを知らせる時刻」の 1 本だけ。鳴ったら、前に処理した時刻から今までに
 * 出すはずだった通知を出し、次の 1 本を仕込み直す。時刻の決め方は :core の NoticePlan にある。
 * 記録や設定が変わったとき・アプリを開いたとき・再起動のあとにも仕込み直す。
 */
object AlarmScheduler {
    const val ACTION_FIRE = "io.github.takosasidev.altroster.action.FIRE"

    private const val PREFS = "alarm_state"
    private const val KEY_LAST_HANDLED = "lastHandledAtEpochMilli"
    private const val REQUEST_CODE = 4001

    /** 記録の保存とアラームの発火が重なっても、片方ずつ処理する。 */
    private val mutex = Mutex()

    /**
     * 次のアラームを仕込み直す。
     *
     * [postDue] が true(アラームが鳴った・再起動した)なら、前に処理した時刻から今までの分の通知を出す。
     * false(記録を変えた・アプリを開いた)なら出さない。利用者がいま画面を見ているので、
     * 過ぎた分をあとから通知しても古い知らせになるだけ。
     */
    suspend fun arm(context: Context, postDue: Boolean = false) = mutex.withLock {
        val appContext = context.applicationContext
        val repo = RosterRepository.get(appContext)
        val now = repo.now()
        val zone = repo.zone()
        val roster = repo.rosterOnce()
        val settings = repo.settingsStore.current()
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (postDue) {
            // 初めて(前に処理した時刻が無い)なら、過去の分は出さない
            val last = prefs.getLong(KEY_LAST_HANDLED, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli) ?: now
            NoticePlan.dueBetween(roster, settings, last, now, zone)
                .forEach { Notifier.post(appContext, it, roster, now, zone) }
        }
        prefs.edit().putLong(KEY_LAST_HANDLED, now.toEpochMilli()).apply()

        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return@withLock
        val pending = pendingIntent(appContext)
        val next = NoticePlan.nextTriggerAfter(roster, settings, now, zone)
        if (next == null) {
            alarmManager.cancel(pending)
        } else {
            set(alarmManager, next.toEpochMilli(), pending)
        }
    }

    /** 正確な時刻のアラームを使えるか。Android 12 以降は、利用者が設定で許可しているときだけ。 */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /**
     * 許可があれば正確な時刻で、無ければおおよその時刻で仕込む
     * (おおよその場合、省電力の間は数分〜十数分ほど遅れることがある。通知が出なくなることはない)。
     */
    private fun set(alarmManager: AlarmManager, triggerAtMillis: Long, pending: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: SecurityException) {
            // 確かめた直後に許可を外された場合。おおよその時刻へ落とす
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
