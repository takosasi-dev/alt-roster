@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.takosasidev.altroster.ui.stats

import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.takosasidev.altroster.ui.common.groupShape
import io.github.takosasidev.altroster.ui.theme.titleTones

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.takosasidev.altroster.core.Format
import io.github.takosasidev.altroster.core.calc.Insights
import io.github.takosasidev.altroster.core.calc.MonthTotal
import io.github.takosasidev.altroster.core.calc.Spending
import io.github.takosasidev.altroster.core.calc.TitleTotal
import io.github.takosasidev.altroster.core.calc.YearlyReview
import io.github.takosasidev.altroster.core.calc.YearlyReviews
import io.github.takosasidev.altroster.ui.RosterViewModel
import io.github.takosasidev.altroster.ui.common.TitleAvatar
import java.time.YearMonth

/** 課金の集計。月ごとの棒グラフ、タイトル別の内訳、気づき。 */
@Composable
fun StatsScreen(vm: RosterViewModel, onOpenYearly: () -> Unit) {
    val roster = vm.roster.collectAsStateWithLifecycle().value
    val nowMonth = YearMonth.from(vm.now().atZone(vm.zone()))
    // 見ている月。今月から何か月前か、で持つ(画面の回転で消えない形にするため)
    var monthsBack by rememberSaveable { mutableStateOf(0) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    val month = nowMonth.minusMonths(monthsBack.toLong())

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text("集計") }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (roster == null) return@Scaffold
        val total = Spending.monthTotal(roster, month)
        val months = remember(roster, month) { Spending.monthly(roster, month, months = 12) }
        val byTitle = remember(roster, month, allTime) {
            if (allTime) Spending.byTitle(roster) else Spending.byTitle(roster, month)
        }
        val insights = remember(roster, nowMonth) { Insights.generate(roster, vm.now(), vm.zone()) }

        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // 月の切り替えと、その月の合計
            Panel(null, groupShape(0, 3)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { monthsBack++ }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の月")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (monthsBack == 0) "今月(${month.year}年${month.monthValue}月)" else "${month.year}年${month.monthValue}月",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(Format.yen(total.total), style = MaterialTheme.typography.displaySmallEmphasized)
                        Text("${total.count} 件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { monthsBack-- }, enabled = monthsBack > 0, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の月")
                    }
                }
            }

            Panel("月ごとの合計(${month.year}年${month.monthValue}月までの 12 か月)", groupShape(1, 3)) {
                MonthlyBars(months)
            }

            Panel("タイトル別", groupShape(2, 3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !allTime, onClick = { allTime = false }, label = { Text("この月") })
                    FilterChip(selected = allTime, onClick = { allTime = true }, label = { Text("全部の期間") })
                }
                if (byTitle.isEmpty()) {
                    Text("この期間の記録はありません。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
                val top = byTitle.maxOfOrNull { it.total } ?: 0L
                byTitle.forEach { TitleBar(it, top) }
            }

            Text(
                "気づき",
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            )
            if (insights.isEmpty()) {
                Panel(null, RoundedCornerShape(24.dp)) {
                    Text("記録がたまると、ここに傾向が出ます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            insights.forEachIndexed { i, line ->
                Panel(null, groupShape(i, insights.size)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(line, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }

            Surface(
                onClick = onOpenYearly,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        "年間の振り返りを見る",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
}

/** 見出しつきの 1 枚。まとまりの中の位置で [shape] が変わる。 */
@Composable
private fun Panel(title: String?, shape: Shape, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = shape) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

/**
 * 月ごとの棒グラフ。棒の上の金額は出さず、月の番号だけを下に出す(金額は読み上げに入れる)。
 * どの月にも空の枠を描き、0 円の月も「無い」と分かるようにする。
 */
@Composable
private fun MonthlyBars(months: List<MonthTotal>) {
    val max = months.maxOf { it.total }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerLowest
    Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        months.forEachIndexed { index, m ->
            val current = index == months.lastIndex
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {
                    contentDescription = "${m.month.year}年${m.month.monthValue}月 ${Format.yen(m.total)}"
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(trackColor), contentAlignment = Alignment.BottomCenter) {
                    if (m.total > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(m.total.toFloat() / max)
                                .clip(RoundedCornerShape(6.dp))
                                // いちばん右が、いま見ている月
                                .background(if (current) barColor else barColor.copy(alpha = 0.35f)),
                        )
                    }
                }
                Text(
                    m.month.monthValue.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TitleBar(total: TitleTotal, top: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TitleAvatar(total.title, size = 36.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(total.title.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Format.yen(total.total)}(${total.count} 件)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // 隙間と右端の点(M3 の既定)は消し、後ろの枠を敷いて全体の長さを見せる
            LinearProgressIndicator(
                progress = { if (top <= 0) 0f else total.total.toFloat() / top },
                color = titleTones(Color(total.title.colorArgb)).second,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** 年間の振り返り。横にめくるスライド。年の途中でも、今までの分を「進行中」として出す。 */
@Composable
fun YearlyScreen(vm: RosterViewModel, onBack: () -> Unit) {
    val roster = vm.roster.collectAsStateWithLifecycle().value
    val now = remember { vm.now() }
    val zone = vm.zone()
    var selectedYear by rememberSaveable { mutableStateOf(now.atZone(zone).year) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("年間の振り返り") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
            )
        },
    ) { padding ->
        if (roster == null) return@Scaffold
        val years = remember(roster) { YearlyReviews.years(roster, now, zone) }
        val review = remember(roster, selectedYear) { YearlyReviews.of(roster, selectedYear, now, zone) }
        val slides = slidesOf(review)
        val pager = rememberPagerState(pageCount = { slides.size })

        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.forEach { y ->
                    FilterChip(selected = y == selectedYear, onClick = { selectedYear = y }, label = { Text("${y}年") })
                }
            }
            Text(
                if (review.inProgress) "集計期間(進行中): ${review.year}年の今までの記録" else "集計期間: ${review.year}年",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f), pageSpacing = 12.dp) { page ->
                val slide = slides[page]
                // カードは中身の高さにして、ページの真ん中に置く(全面に伸ばすと間延びするため)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(slide.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                        }
                        Text(
                            slide.heading,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        Text(
                            slide.value,
                            style = MaterialTheme.typography.displaySmallEmphasized,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                        Text(slide.note, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                }
                }
            }
            // いま何枚目かの印。色だけでなく大きさでも分ける
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                slides.indices.forEach { i ->
                    val current = i == pager.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (current) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Text(
                "${pager.currentPage + 1} / ${slides.size}(横にめくれます)",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class Slide(val heading: String, val value: String, val note: String, val icon: ImageVector)

private fun slidesOf(r: YearlyReview): List<Slide> = listOf(
    Slide("${r.year}年の課金の合計", Format.yen(r.totalSpend), "${r.spendCount} 件の記録", Icons.Filled.Payments),
    Slide(
        "いちばん課金したタイトル",
        r.topSpendTitle?.title?.name ?: "なし",
        r.topSpendTitle?.let { "${Format.yen(it.total)}(${it.count} 件)" } ?: "課金の記録がありません",
        Icons.Filled.EmojiEvents,
    ),
    Slide(
        "いちばん課金が多かった月",
        r.peakMonth?.let { "${it.month.monthValue}月" } ?: "なし",
        r.peakMonth?.let { Format.yen(it.total) } ?: "課金の記録がありません",
        Icons.Filled.CalendarMonth,
    ),
    Slide(
        "いちばん遊んだタイトル",
        r.mostPlayed?.first?.name ?: "なし",
        r.mostPlayed?.let { "タスクを ${it.second} 回チェック" } ?: "タスクのチェックの記録がありません",
        Icons.Filled.SportsEsports,
    ),
    Slide("対応したイベント", "${r.eventsCompleted} 件", "タスクのチェックは合計 ${r.tasksChecked} 回", Icons.Filled.TaskAlt),
)
