@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.takosasidev.altroster.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.calc.Stamina
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.ui.theme.LocalStatusColors
import io.github.takosasidev.altroster.ui.theme.PaletteColors
import io.github.takosasidev.altroster.ui.theme.titleTones
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 画面が前に出ている間、15 秒ごとに今の時刻を流す。
 * スタミナの値とカウントダウンは時刻から計算するので、これが変わるたびに描き直される。
 */
@Composable
fun rememberNow(now: () -> Instant): Instant {
    val owner = LocalLifecycleOwner.current
    var current by remember { mutableStateOf(now()) }
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                current = now()
                delay(15_000)
            }
        }
    }
    return current
}

/** タイトルのアイコンに選べる、一般的な図柄。実在のゲームの画像やロゴは同梱しない。 */
val TitleIcons: Map<String, ImageVector> = linkedMapOf(
    "gamepad" to Icons.Filled.SportsEsports,
    "star" to Icons.Filled.Star,
    "heart" to Icons.Filled.Favorite,
    "bolt" to Icons.Filled.Bolt,
    "shield" to Icons.Filled.Shield,
    "castle" to Icons.Filled.Castle,
    "rocket" to Icons.Filled.RocketLaunch,
    "music" to Icons.Filled.MusicNote,
    "pets" to Icons.Filled.Pets,
    "puzzle" to Icons.Filled.Extension,
    "car" to Icons.Filled.DirectionsCar,
    "ball" to Icons.Filled.SportsSoccer,
    "sparkle" to Icons.Filled.AutoAwesome,
    "diamond" to Icons.Filled.Diamond,
    "fire" to Icons.Filled.LocalFireDepartment,
    "globe" to Icons.Filled.Public,
)

/** 読み上げに渡すアイコンの名前(保存するキーは英語なので、そのまま読ませない)。 */
val TitleIconLabels: Map<String, String> = mapOf(
    "gamepad" to "ゲームパッド", "star" to "星", "heart" to "ハート", "bolt" to "稲妻", "shield" to "盾",
    "castle" to "城", "rocket" to "ロケット", "music" to "音符", "pets" to "足あと", "puzzle" to "パズル",
    "car" to "車", "ball" to "ボール", "sparkle" to "きらめき", "diamond" to "ダイヤ", "fire" to "炎", "globe" to "地球",
)

/** 色の上に載せる文字やアイコンの色。明るい色の上は黒、暗い色の上は白。 */
fun onColor(background: Color): Color = if (background.luminance() > 0.5f) Color.Black else Color.White

/** まとまったリストの 1 枚の角。端だけ大きく丸め、間は小さくする(Android 16 の設定の画面と同じ形)。 */
fun groupShape(index: Int, count: Int): Shape {
    val big = 24.dp
    val small = 6.dp
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** タイトルの丸い目印。内蔵のアイコンか、名前の頭文字を、タイトルの色の淡い地の上に濃い色で出す。 */
@Composable
fun TitleAvatar(name: String, iconKey: String?, colorArgb: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val (background, on) = titleTones(Color(colorArgb))
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        val icon = TitleIcons[iconKey]
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = on, modifier = Modifier.size(size * 0.52f))
        } else {
            val trimmed = name.trim()
            // 絵文字などの 2 つで 1 文字の物を途中で切らない
            val initial = if (trimmed.isEmpty()) "?" else trimmed.substring(0, trimmed.offsetByCodePoints(0, 1))
            Text(initial, color = on, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun TitleAvatar(title: GameTitle, modifier: Modifier = Modifier, size: Dp = 40.dp) =
    TitleAvatar(title.name, title.iconKey, title.colorArgb, modifier, size)

/** スタミナの状況の文。「18:10 に満タン(あと 3時間40分)」。 */
fun staminaStatus(resource: StaminaResource, now: Instant, zone: ZoneId): String {
    val fullAt = Stamina.fullAt(resource) ?: return "回復しない設定です"
    return if (!fullAt.isAfter(now)) {
        "満タン(${Format.clock(fullAt, now, zone)} から)"
    } else {
        "${Format.clock(fullAt, now, zone)} に満タン(あと ${Format.duration(Duration.between(now, fullAt))})"
    }
}

/**
 * スタミナの横棒のゲージ。
 * 満タンに近づくと色が変わる。色だけに頼らないよう、状態はアイコンの形でも分ける
 * (通常 = 稲妻、9 割以上 = 警告の三角、満タン = 丸にチェック)。
 */
@Composable
fun StaminaGauge(
    resource: StaminaResource,
    accent: Color,
    now: Instant,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    label: String = resource.resourceName,
    onClick: (() -> Unit)? = null,
) {
    val value = Stamina.currentValue(resource, now)
    val fraction = Stamina.fraction(resource, now)
    val full = value >= resource.maxValue
    val near = !full && fraction >= 0.9f
    val target = when {
        full -> MaterialTheme.colorScheme.error
        near -> LocalStatusColors.current.warning
        // タイトルの色は、地の明るさに合わせて読める濃さにしてから使う
        else -> titleTones(accent).second
    }
    val barColor by animateColorAsState(target, tween(600), label = "gaugeColor")
    val animated by animateFloatAsState(fraction, spring(stiffness = Spring.StiffnessLow), label = "gaugeLength")
    val click = if (onClick != null) Modifier.clickable(onClickLabel = "今の値を記録する", onClick = onClick) else Modifier

    Column(modifier.fillMaxWidth().then(click).heightIn(min = 48.dp).padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when {
                    full -> Icons.Filled.CheckCircle
                    near -> Icons.Filled.Warning
                    else -> Icons.Filled.Bolt
                },
                contentDescription = when {
                    full -> "満タン"
                    near -> "満タン間近"
                    else -> null
                },
                tint = barColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            Text(
                "$value / ${resource.maxValue}",
                style = MaterialTheme.typography.titleSmallEmphasized,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        // 隙間と右端の点(M3 の既定)は消し、後ろの枠を敷いて全体の長さを見せる
        LinearProgressIndicator(
            progress = { animated },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(10.dp).clip(RoundedCornerShape(5.dp)),
        )
        Text(
            staminaStatus(resource, now, zone),
            style = MaterialTheme.typography.bodySmall,
            // 満タンは文字も危険の色にする(タイトルの色が赤でも、満タンと見分けられるように)
            color = if (full) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * タスクの 1 行。行のどこを押してもチェックが切り替わる(押す所は 48dp 以上)。
 * チェックを付けた瞬間だけ、チェックの箱がばねで弾み、文字に取り消し線が付き、軽く振動する。
 */
@Composable
fun TaskCheckRow(label: String, sub: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        // 画面を開いた時点でチェック済みの行は弾ませない
        if (checked && !previous) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
        previous = checked
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.scale(scale.value))
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                label,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 5.dp),
    )
}

/** 消す前などの確認。[danger] なら、実行のボタンを警告の色にする。 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}

/** 色の候補を丸で並べる。選んでいる色にはチェックを重ねる(色の名前は読み上げに出す)。 */
@Composable
fun ColorPicker(selectedArgb: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PaletteColors.forEach { (name, color) ->
            val argb = color.toArgb()
            val selected = argb == selectedArgb
            Box(
                Modifier
                    .size(48.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(argb) })
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 3.dp else 0.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = onColor(color))
                }
            }
        }
    }
}

/** 数字だけを受け付ける入力欄。 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it in '0'..'9' }.take(9)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TimePickDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("決定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun DatePickDialog(initial: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // DatePicker は日付を UTC の 0 時のミリ秒でやり取りする
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            ) { Text("決定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    ) {
        DatePicker(state = state)
    }
}

/**
 * まだ何も登録していない時の案内。絵は Canvas で描いた物(カードが重なった名簿と、スタミナのゲージ)。
 */
@Composable
fun EmptyState(message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primaryContainer
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(160.dp, 120.dp)) {
            val w = size.width
            val h = size.height
            val radius = CornerRadius(w * 0.06f)
            // 後ろのカード 2 枚と、手前のカード
            drawRoundRect(line, Offset(w * 0.22f, h * 0.04f), Size(w * 0.66f, h * 0.6f), radius)
            drawRoundRect(soft, Offset(w * 0.14f, h * 0.16f), Size(w * 0.72f, h * 0.62f), radius)
            drawRoundRect(primary, Offset(w * 0.06f, h * 0.3f), Size(w * 0.78f, h * 0.64f), radius)
            // 手前のカードの上の、丸い目印とゲージ
            drawCircle(soft, radius = h * 0.09f, center = Offset(w * 0.2f, h * 0.5f))
            drawRoundRect(soft, Offset(w * 0.32f, h * 0.46f), Size(w * 0.44f, h * 0.08f), CornerRadius(h * 0.04f))
            drawRoundRect(line, Offset(w * 0.14f, h * 0.72f), Size(w * 0.62f, h * 0.08f), CornerRadius(h * 0.04f))
            drawRoundRect(soft, Offset(w * 0.14f, h * 0.72f), Size(w * 0.4f, h * 0.08f), CornerRadius(h * 0.04f))
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
        )
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
