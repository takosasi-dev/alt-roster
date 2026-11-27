package io.github.takosasidev.altroster.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.takosasidev.altroster.widget.RosterWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * アラームが鳴ったとき、端末を再起動したとき、時計や時間帯が変わったとき、アプリを更新したときに呼ばれる。
 * どの場合も、出すはずだった通知を出して、次のアラームを仕込み直す(再起動でアラームは消えるため)。
 *
 * onReceive は同期の関数だが、DB を読む必要があるので、goAsync() で猶予をもらって IO のスレッドで処理する。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { AlarmScheduler.arm(appContext, postDue = true) }
            runCatching { RosterWidget.refresh(appContext) }
            pending.finish()
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            AlarmScheduler.ACTION_FIRE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // 正確なアラームを設定で許可した時(Android 12 以降)。許可の前に仕込んだ 1 本は
            // おおよその時刻のままなので、ここで正確な時刻に仕込み直す
            ACTION_EXACT_ALARM_PERMISSION_CHANGED,
        )

        /** AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED(API 31)と同じ文字列。 */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
