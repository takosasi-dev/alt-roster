@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.takosasidev.altroster.ui.stats

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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import io.github.takosasidev.altroster.ui.common.SectionHeader
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

    Scaffold(topBar = { TopAppBar(title = { Text("集計") }) }) { padding ->
        if (roster == null) return@Scaffold
        val total = Spending.monthTotal(roster, month)
        val months = remember(roster, month) { Spending.monthly(roster, month, months = 12) }
        val byTitle = remember(roster, month, allTime) {
            if (allTime) Spending.byTitle(roster) else Spending.byTitle(roster, month)
        }
        val insights = remember(roster, nowMonth) { Insights.generate(roster, vm.now(), vm.zone()) }

        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthsBack++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前の月")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (monthsBack == 0) "今月(${month.year}年${month.monthValue}月)" else "${month.year}年${month.monthValue}月",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(Format.yen(total.total), style = MaterialTheme.typography.headlineMedium)
                    Text("${total.count} 件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { monthsBack-- }, enabled = monthsBack > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "次の月")
                }
            }

            SectionHeader("月ごとの合計(${month.year}年${month.monthValue}月までの 12 か月)")
            MonthlyBars(months)

            SectionHeader("タイトル別")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !allTime, onClick = { allTime = false }, label = { Text("この月") })
                FilterChip(selected = allTime, onClick = { allTime = true }, label = { Text("全部の期間") })
            }
            if (byTitle.isEmpty()) {
                Text("この期間の記録はありません。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            val top = byTitle.maxOfOrNull { it.total } ?: 0L
            byTitle.forEach { TitleBar(it, top) }

            SectionHeader("気づき")
            if (insights.isEmpty()) {
                Text("記録がたまると、ここに傾向が出ます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            insights.forEach { line ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(line, modifier = Modifier.padding(start = 8.dp))
                }
            }

            OutlinedButton(onClick = onOpenYearly, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 32.dp)) {
                Text("年間の振り返りを見る")
            }
        }
    }
}

/** 月ごとの棒グラフ。棒の上の金額は出さず、月の番号だけを下に出す(金額は読み上げに入れる)。 */
@Composable
private fun MonthlyBars(months: List<MonthTotal>) {
    val max = months.maxOf { it.total }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        months.forEachIndexed { index, m ->
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {
                    contentDescription = "${m.month.year}年${m.month.monthValue}月 ${Format.yen(m.total)}"
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(trackColor), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(m.total.toFloat() / max)
                            // いちばん右が、いま見ている月
                            .background(if (index == months.lastIndex) barColor else barColor.copy(alpha = 0.55f)),
                    )
                }
                Text(m.month.monthValue.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TitleBar(total: TitleTotal, top: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TitleAvatar(total.title, size = 32.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row {
                Text(total.title.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Format.yen(total.total)}(${total.count} 件)", style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { if (top <= 0) 0f else total.total.toFloat() / top },
                color = Color(total.title.colorArgb),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
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
                Card(
                    Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(slide.heading, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(
                            slide.value,
                            style = MaterialTheme.typography.displaySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                        Text(slide.note, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
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

private data class Slide(val heading: String, val value: String, val note: String)

private fun slidesOf(r: YearlyReview): List<Slide> = listOf(
    Slide("${r.year}年の課金の合計", Format.yen(r.totalSpend), "${r.spendCount} 件の記録"),
    Slide(
        "いちばん課金したタイトル",
        r.topSpendTitle?.title?.name ?: "なし",
        r.topSpendTitle?.let { "${Format.yen(it.total)}(${it.count} 件)" } ?: "課金の記録がありません",
    ),
    Slide(
        "いちばん課金が多かった月",
        r.peakMonth?.let { "${it.month.monthValue}月" } ?: "なし",
        r.peakMonth?.let { Format.yen(it.total) } ?: "課金の記録がありません",
    ),
    Slide(
        "いちばん遊んだタイトル",
        r.mostPlayed?.first?.name ?: "なし",
        r.mostPlayed?.let { "タスクを ${it.second} 回チェック" } ?: "タスクのチェックの記録がありません",
    ),
    Slide("対応したイベント", "${r.eventsCompleted} 件", "タスクのチェックは合計 ${r.tasksChecked} 回"),
)
