@file:OptIn(ExperimentalLayoutApi::class)

package io.github.takosasidev.altroster.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.calc.ResetSpec
import io.github.takosasidev.altroster.core.calc.Stamina
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.GameAccount
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.ui.theme.PaletteColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val WEEKDAY_LABELS = listOf("月", "火", "水", "木", "金", "土", "日")

/** タスクのリセットの説明。「毎日 05:00」「毎週 月曜 05:00」。 */
fun resetLabel(task: DailyTask): String {
    val spec = ResetSpec.of(task)
    val time = AppSettings.formatTime(spec.time.hour, spec.time.minute)
    return when (spec.type) {
        ResetType.DAILY -> "毎日 $time"
        ResetType.WEEKLY -> "毎週 ${WEEKDAY_LABELS[spec.dayOfWeek.value - 1]}曜 $time"
    }
}

/** 編集のダイアログの共通の枠。中身が長いときは縦に動かせる。 */
@Composable
private fun EditDialog(
    title: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("削除する", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = canSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** タイトルの登録と編集。名前は利用者が打ち込む。アイコンは内蔵の図柄か頭文字。 */
@Composable
fun TitleEditDialog(initial: GameTitle?, knownTags: List<String>, onSave: (GameTitle) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var colorArgb by remember { mutableStateOf(initial?.colorArgb ?: PaletteColors.first().second.toArgb()) }
    var iconKey by remember { mutableStateOf(initial?.iconKey) }
    var tagsText by remember { mutableStateOf(initial?.tags.orEmpty().joinToString(", ")) }

    fun tags(): List<String> = GameTitle.normalizeTags(tagsText.split(",", "、"))

    EditDialog(
        title = if (initial == null) "タイトルを追加" else "タイトルを編集",
        canSave = name.isNotBlank(),
        onSave = {
            val base = initial ?: GameTitle(name = "", colorArgb = colorArgb)
            onSave(base.copy(name = name.trim(), colorArgb = colorArgb, iconKey = iconKey, tags = tags()))
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("タイトルの名前") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("色", style = MaterialTheme.typography.labelLarge)
        ColorPicker(selectedArgb = colorArgb, onSelect = { colorArgb = it })

        Text("アイコン", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // 先頭は「頭文字」(アイコン無し)
            (listOf<String?>(null) + TitleIcons.keys).forEach { key ->
                val selected = key == iconKey
                Box(
                    Modifier
                        .size(48.dp)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { iconKey = key })
                        .semantics { contentDescription = key ?: "頭文字" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        TitleAvatar(name.ifBlank { "A" }, key, colorArgb, size = 34.dp)
                    }
                }
            }
        }

        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it.take(120) },
            label = { Text("タグ") },
            supportingText = { Text("カンマで区切って入れます(例: RPG, 優先)。ホームの絞り込みに使えます。") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val unused = knownTags.filter { it !in tags() }
        if (unused.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                unused.forEach { tag ->
                    SuggestionChip(onClick = { tagsText = (tags() + tag).joinToString(", ") }, label = { Text(tag) })
                }
            }
        }
    }
}

/**
 * アカウントの登録と編集。
 * メモの欄には、パスワードなどを書かないよう、いつも見える注意書きを付ける
 * (このアプリは認証に使える情報を保存しない方針で、メモは暗号化していない)。
 */
@Composable
fun AccountEditDialog(initial: GameAccount?, titleId: Long, now: () -> Instant, onSave: (GameAccount) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(initial?.label ?: "メイン") }
    var memo by remember { mutableStateOf(initial?.loginHintMemo.orEmpty()) }

    EditDialog(
        title = if (initial == null) "アカウントを追加" else "アカウントを編集",
        canSave = label.isNotBlank(),
        onSave = {
            val base = initial ?: GameAccount(gameTitleId = titleId, label = "", createdAt = now())
            onSave(base.copy(label = label.trim(), loginHintMemo = memo.trim().ifEmpty { null }))
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            label = { Text("アカウントの呼び名") },
            placeholder = { Text("メイン、サブ1 など") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it.take(300) },
            label = { Text("ログインの手がかりのメモ(任意)") },
            placeholder = { Text("例: メールのほうで連携。パスワードは書かない") },
            supportingText = {
                Text(
                    "パスワードや引き継ぎコードなど、ログインに使える情報は保存しないでください。" +
                        "このメモは暗号化されず、バックアップにもそのまま入ります。",
                )
            },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** スタミナの種別の登録と編集。保存すると、満タンの時刻が計算し直される。 */
@Composable
fun StaminaEditDialog(
    initial: StaminaResource?,
    accountId: Long,
    now: () -> Instant,
    onSave: (StaminaResource) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.resourceName ?: "スタミナ") }
    var max by remember { mutableStateOf(initial?.maxValue?.toString().orEmpty()) }
    var interval by remember { mutableStateOf(initial?.recoveryIntervalMinutes?.toString() ?: "5") }
    var value by remember { mutableStateOf(initial?.let { Stamina.currentValue(it, now()).toString() } ?: "0") }
    var notifyOnFull by remember { mutableStateOf(initial?.notifyOnFull ?: true) }
    var percent by remember { mutableStateOf(initial?.notifyThresholdPercent?.toString().orEmpty()) }

    val maxInt = max.toIntOrNull()
    val intervalInt = interval.toIntOrNull()
    val valueInt = value.toIntOrNull()
    val percentInt = percent.toIntOrNull()
    val maxError = if (max.isNotEmpty() && (maxInt == null || maxInt < 1)) "1 以上で入れてください" else null
    val intervalError = if (interval.isNotEmpty() && (intervalInt == null || intervalInt < 1)) "1 分以上で入れてください" else null
    val valueError = if (valueInt != null && maxInt != null && valueInt > maxInt) "最大値を超えています" else null
    val percentError = if (percent.isNotEmpty() && (percentInt == null || percentInt !in 1..99)) "1〜99 で入れてください" else null
    val valid = name.isNotBlank() && maxInt != null && maxInt >= 1 && intervalInt != null && intervalInt >= 1 &&
        valueInt != null && valueInt <= maxInt && percentError == null

    EditDialog(
        title = if (initial == null) "スタミナを追加" else "スタミナを編集",
        canSave = valid,
        onSave = {
            if (maxInt != null && intervalInt != null && valueInt != null) {
                val at = now()
                onSave(
                    if (initial == null) {
                        StaminaResource(
                            gameAccountId = accountId,
                            resourceName = name.trim(),
                            maxValue = maxInt,
                            recoveryIntervalMinutes = intervalInt,
                            recordedValue = valueInt,
                            recordedAt = at,
                            notifyOnFull = notifyOnFull,
                            notifyThresholdPercent = percentInt,
                        )
                    } else {
                        Stamina.edited(initial, name.trim(), maxInt, intervalInt, valueInt, notifyOnFull, percentInt, at)
                    },
                )
            }
        },
        onDismiss = onDismiss,
        onDelete = onDelete,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            label = { Text("名前(AP、BP、スタミナ など)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField(max, { max = it }, "最大値", error = maxError)
        NumberField(interval, { interval = it }, "1 回復するまでの時間", error = intervalError, suffix = "分")
        NumberField(value, { value = it }, "今の値", error = valueError)
        if (maxInt != null && intervalInt != null && valueInt != null && valid) {
            val at = now()
            val preview = StaminaResource(0, accountId, name, maxInt, intervalInt, valueInt, at)
            Text(
                "この値で保存すると: " + staminaStatus(preview, at, ZoneId.systemDefault()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SwitchRow("満タンになったら通知する", notifyOnFull) { notifyOnFull = it }
        NumberField(percent, { percent = it.take(2) }, "この割合に届いたら通知する(任意)", error = percentError, suffix = "%")
    }
}

/** ホームからの、今の値だけの書き直し。今の時刻を新しい基準にして数え直す。 */
@Composable
fun StaminaQuickDialog(resource: StaminaResource, now: () -> Instant, onSave: (StaminaResource) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(Stamina.currentValue(resource, now()).toString()) }
    val valueInt = value.toIntOrNull()
    val error = if (valueInt != null && valueInt > resource.maxValue) "最大値(${resource.maxValue})を超えています" else null

    EditDialog(
        title = "${resource.resourceName} の今の値",
        canSave = valueInt != null && error == null,
        onSave = { if (valueInt != null) onSave(resource.copy(recordedValue = valueInt, recordedAt = now())) },
        onDismiss = onDismiss,
    ) {
        NumberField(value, { value = it }, "今の値(最大 ${resource.maxValue})", error = error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { value = "0" }) { Text("0 にする") }
            OutlinedButton(onClick = { value = resource.maxValue.toString() }) { Text("満タンにする") }
        }
    }
}

/** デイリー・ウィークリーのタスクの登録と編集。 */
@Composable
fun TaskEditDialog(
    initial: DailyTask?,
    accountId: Long,
    onSave: (DailyTask) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val initialSpec = initial?.let(ResetSpec::of)
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var type by remember { mutableStateOf(initial?.resetType ?: ResetType.DAILY) }
    var time by remember { mutableStateOf(initialSpec?.time ?: ResetSpec.DEFAULT_TIME) }
    var day by remember { mutableStateOf(initialSpec?.dayOfWeek ?: DayOfWeek.MONDAY) }
    var pickingTime by remember { mutableStateOf(false) }

    EditDialog(
        title = if (initial == null) "タスクを追加" else "タスクを編集",
        canSave = label.isNotBlank(),
        onSave = {
            val base = initial ?: DailyTask(gameAccountId = accountId, label = "", resetType = type, resetTime = "")
            onSave(base.copy(label = label.trim(), resetType = type, resetTime = ResetSpec(type, time, day).encode()))
        },
        onDismiss = onDismiss,
        onDelete = onDelete,
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(60) },
            label = { Text("タスクの名前") },
            placeholder = { Text("ログインボーナス など") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == ResetType.DAILY, onClick = { type = ResetType.DAILY }, label = { Text("毎日") })
            FilterChip(selected = type == ResetType.WEEKLY, onClick = { type = ResetType.WEEKLY }, label = { Text("毎週") })
        }
        if (type == ResetType.WEEKLY) {
            Text("リセットの曜日", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(selected = day == d, onClick = { day = d }, label = { Text(WEEKDAY_LABELS[d.value - 1]) })
                }
            }
        }
        OutlinedButton(onClick = { pickingTime = true }) {
            Text("リセットの時刻: ${AppSettings.formatTime(time.hour, time.minute)}")
        }
        Text(
            "この端末の時刻で数えます。この時刻を過ぎると、チェックが自動で外れます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (pickingTime) {
        TimePickDialog(time, onConfirm = { time = it; pickingTime = false }, onDismiss = { pickingTime = false })
    }
}

/** イベントの期限の登録と編集。 */
@Composable
fun EventEditDialog(
    initial: GameEvent?,
    titleId: Long,
    now: () -> Instant,
    zone: ZoneId,
    onSave: (GameEvent) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val initialEnd = initial?.endAt?.atZone(zone)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var date by remember { mutableStateOf<LocalDate>(initialEnd?.toLocalDate() ?: now().atZone(zone).toLocalDate().plusDays(7)) }
    var time by remember { mutableStateOf<LocalTime>(initialEnd?.toLocalTime()?.withSecond(0)?.withNano(0) ?: LocalTime.of(23, 59)) }
    var hours by remember { mutableStateOf((initial?.reminderBeforeHours ?: 24).toString()) }
    var completed by remember { mutableStateOf(initial?.isCompleted ?: false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    val endAt = date.atTime(time).atZone(zone).toInstant()
    val hoursInt = hours.toIntOrNull()

    EditDialog(
        title = if (initial == null) "イベントを追加" else "イベントを編集",
        canSave = name.isNotBlank() && hoursInt != null,
        onSave = {
            val base = initial ?: GameEvent(gameTitleId = titleId, name = "", endAt = endAt)
            onSave(base.copy(name = name.trim(), endAt = endAt, reminderBeforeHours = hoursInt ?: 24, isCompleted = completed))
        },
        onDismiss = onDismiss,
        onDelete = onDelete,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("イベントの名前") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("終了の日時", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickingDate = true }) { Text("${date.year}/${date.monthValue}/${date.dayOfMonth}") }
            OutlinedButton(onClick = { pickingTime = true }) { Text(AppSettings.formatTime(time.hour, time.minute)) }
        }
        val left = java.time.Duration.between(now(), endAt)
        Text(
            if (left.isNegative) "この日時はもう過ぎています" else "あと ${Format.duration(left)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (left.isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberField(hours, { hours = it.take(3) }, "終了の何時間前に通知するか", suffix = "時間前")
        if (initial != null) {
            SwitchRow("対応済みにする(通知とホームから外す)", completed) { completed = it }
        }
    }
    if (pickingDate) {
        DatePickDialog(date, onConfirm = { date = it; pickingDate = false }, onDismiss = { pickingDate = false })
    }
    if (pickingTime) {
        TimePickDialog(time, onConfirm = { time = it; pickingTime = false }, onDismiss = { pickingTime = false })
    }
}
