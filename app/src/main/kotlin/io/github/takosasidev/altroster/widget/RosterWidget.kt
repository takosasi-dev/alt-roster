package io.github.takosasidev.altroster.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.takosasidev.altroster.MainActivity
import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.calc.Stamina
import io.github.takosasidev.altroster.data.RosterRepository

/** ホーム画面のウィジェット。全部のタイトルのスタミナ(満タンが早い順)と、いちばん近い期限を出す。 */
class RosterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RosterWidget
}

private data class StaminaLine(val name: String, val status: String, val isFull: Boolean, val colorArgb: Int)

private data class WidgetData(val lines: List<StaminaLine>, val more: Int, val deadline: String?)

/** 1 つのウィジェットに出すスタミナの行の数。これを超えた分は「ほか N 件」にする。 */
private const val MAX_LINES = 4

object RosterWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // provideContent は戻ってこないので、DB はその前に読み、例外も握る。
        // ここで投げるとホーム画面に「読み込めません」が出てしまう。
        val data = runCatching { load(context) }.getOrNull()
        provideContent { WidgetBody(data) }
    }

    /** 記録が変わったとき・アラームが鳴ったときに呼ぶ。置いてあるウィジェットを全部描き直す。 */
    suspend fun refresh(context: Context) = updateAll(context)

    /**
     * 出すのは「満タンになる時刻」と「期限の日時」。今の値や残り時間は出さない。
     * ウィジェットは 30 分おきにしか描き直されないので、時間とともに変わる値を出すと古い数字が残る。
     */
    private suspend fun load(context: Context): WidgetData {
        val repo = RosterRepository.get(context)
        val roster = repo.rosterOnce()
        val now = repo.now()
        val zone = repo.zone()

        val all = roster.activeStaminas
            .mapNotNull { r -> Stamina.fullAt(r)?.let { r to it } }
            .sortedBy { it.second }
        val lines = all.take(MAX_LINES).map { (r, fullAt) ->
            val full = !fullAt.isAfter(now)
            StaminaLine(
                name = "${roster.accountDisplayName(r.gameAccountId)} ${r.resourceName}",
                status = if (full) "満タン" else "${Format.clock(fullAt, now, zone)} 満タン",
                isFull = full,
                colorArgb = roster.titleOfAccount(r.gameAccountId)?.colorArgb ?: 0,
            )
        }
        val deadline = roster.activeEvents
            .filter { !it.isCompleted && it.endAt.isAfter(now) }
            .minByOrNull { it.endAt }
            ?.let { "期限: ${it.name} ${Format.clock(it.endAt, now, zone)} まで" }
        return WidgetData(lines, (all.size - lines.size).coerceAtLeast(0), deadline)
    }
}

@Composable
private fun WidgetBody(data: WidgetData?) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            when {
                data == null -> Note("読み込めませんでした。タップしてアプリを開いてください。")
                data.lines.isEmpty() && data.deadline == null ->
                    Note("スタミナやイベントを登録すると、ここに状況が出ます。")
                else -> {
                    data.lines.forEach { StaminaRow(it) }
                    if (data.more > 0) Note("ほか ${data.more} 件")
                    data.deadline?.let { Note(it) }
                }
            }
        }
    }
}

@Composable
private fun StaminaRow(line: StaminaLine) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // タイトルの色の目印
        Box(modifier = GlanceModifier.width(4.dp).height(14.dp).background(Color(line.colorArgb))) {}
        Text(
            text = line.name,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().padding(start = 6.dp, end = 6.dp),
        )
        Text(
            // 色だけに頼らず、満タンは印(●)でも分かるようにする
            text = if (line.isFull) "● ${line.status}" else line.status,
            style = TextStyle(
                color = if (line.isFull) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = if (line.isFull) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        maxLines = 2,
        modifier = GlanceModifier.padding(top = 2.dp),
    )
}
