package io.github.takosasidev.altroster.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.takosasidev.altroster.MainActivity
import io.github.takosasidev.altroster.R
import io.github.takosasidev.altroster.core.calc.Notice
import io.github.takosasidev.altroster.core.calc.NoticeText
import io.github.takosasidev.altroster.core.model.Roster
import java.time.Instant
import java.time.ZoneId

/** 通知を実際に出す所。文は :core の NoticeText が作る。Logcat には何も書かない。 */
object Notifier {
    private const val CHANNEL_STAMINA = "stamina"
    private const val CHANNEL_EVENT = "event"
    private const val CHANNEL_TASK = "task_reset"
    private const val CHANNEL_DIGEST = "digest"

    /** 種類ごとに番号の帯を分ける。同じスタミナ・同じイベントの通知は、前の物を置き換える。 */
    private const val ID_STAMINA = 1_000_000
    private const val ID_EVENT = 2_000_000
    private const val ID_TASK = 3_000_000
    private const val ID_DIGEST = 3_000_001

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 通知できたら true。権限が無い・相手が消えている・まとめる事が無いときは、静かに false。 */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() で確かめ、失敗も握っている
    fun post(context: Context, notice: Notice, roster: Roster, now: Instant, zone: ZoneId): Boolean = runCatching {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val message = NoticeText.of(notice, roster, now, zone) ?: return false
        ensureChannels(context)

        val (channel, id) = when (notice) {
            is Notice.StaminaFull -> CHANNEL_STAMINA to ID_STAMINA + (notice.resourceId % 900_000).toInt()
            is Notice.StaminaThreshold -> CHANNEL_STAMINA to ID_STAMINA + (notice.resourceId % 900_000).toInt()
            is Notice.EventReminder -> CHANNEL_EVENT to ID_EVENT + (notice.eventId % 900_000).toInt()
            is Notice.TasksReset -> CHANNEL_TASK to ID_TASK
            is Notice.DigestTime -> CHANNEL_DIGEST to ID_DIGEST
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setContentIntent(open)
            .setAutoCancel(true)

        if (notice is Notice.EventReminder) {
            // 終了の時刻までのカウントダウンを、通知の時刻の欄に出す
            roster.events.find { it.id == notice.eventId }?.let { event ->
                builder.setWhen(event.endAt.toEpochMilli())
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
        }

        // 権限が無い端末では SecurityException が飛ぶので、runCatching の内側で呼ぶ
        manager.notify(id, builder.build())
        true
    }.getOrDefault(false)

    /** 通知のチャンネル。同じ id で作り直しても、利用者が変えた設定は壊れない。 */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(CHANNEL_STAMINA, context.getString(R.string.channel_stamina), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_EVENT, context.getString(R.string.channel_event), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_TASK, context.getString(R.string.channel_task), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CHANNEL_DIGEST, context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach(manager::createNotificationChannel)
    }
}
