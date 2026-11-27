package io.github.takosasidev.altroster

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.takosasidev.altroster.core.calc.TaskReset
import io.github.takosasidev.altroster.core.model.AppSettings
import io.github.takosasidev.altroster.core.model.DailyTask
import io.github.takosasidev.altroster.core.model.DarkMode
import io.github.takosasidev.altroster.core.model.GachaLog
import io.github.takosasidev.altroster.core.model.GameAccount
import io.github.takosasidev.altroster.core.model.GameEvent
import io.github.takosasidev.altroster.core.model.GameTitle
import io.github.takosasidev.altroster.core.model.ResetType
import io.github.takosasidev.altroster.core.model.Roster
import io.github.takosasidev.altroster.core.model.StaminaResource
import io.github.takosasidev.altroster.core.model.TaskCheckIn
import io.github.takosasidev.altroster.core.model.ThemeColorMode
import io.github.takosasidev.altroster.data.RosterRepository
import io.github.takosasidev.altroster.ui.RosterViewModel
import io.github.takosasidev.altroster.ui.gacha.GachaEntryScreen
import io.github.takosasidev.altroster.ui.gacha.GachaListScreen
import io.github.takosasidev.altroster.ui.gacha.NEW_GACHA_ID
import io.github.takosasidev.altroster.ui.home.HomeScreen
import io.github.takosasidev.altroster.ui.settings.BackupScreen
import io.github.takosasidev.altroster.ui.settings.SettingsScreen
import io.github.takosasidev.altroster.ui.stats.StatsScreen
import io.github.takosasidev.altroster.ui.stats.YearlyScreen
import io.github.takosasidev.altroster.ui.theme.AltRosterTheme
import io.github.takosasidev.altroster.ui.title.TitleDetailScreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 画面を PC の上で画像にする(端末が無くても見た目を確かめるため)。
 * `gradlew testDebugUnitTest -Pshots` のときだけ app/build/outputs/roborazzi/ に PNG を書く。
 * 画像は比べない(合否を見ない)。見るのは人。
 * タイトル・イベント・通貨の名前はすべて架空の物。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {

    // テスト用の空の Activity を、マニフェストに書かずに登録する(debug の APK に物を足さないため)
    @get:Rule(order = 0)
    val registerActivity = object : TestWatcher() {
        override fun starting(description: Description?) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * 見た目を確かめたい状態を一通り入れる: 満タン・満タン間近・通常のスタミナ、アカウントが 2 つのタイトル、
     * 長い名前、24 時間以内・3 日以内・通常・終了・対応済みのイベント、毎日と毎週のタスク(一部チェック済み)、
     * 12 か月分の課金、アーカイブしたタイトルとアカウント。
     * 過去の日付のチェックを入れるため、バックアップからの復元と同じ道(replaceAll)を通す。id は 1 から振る。
     */
    private fun seed(repo: RosterRepository) = runBlocking {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun ago(minutes: Long) = now.minus(Duration.ofMinutes(minutes))
        fun inHours(hours: Long) = now.plus(Duration.ofHours(hours))

        val titles = listOf(
            GameTitle(1, "ほうき星クロニクル", "star", 0xFF3F51B5.toInt(), 1, tags = listOf("RPG", "毎日")),
            GameTitle(2, "ネオンドリフト・レーサーズ:ワールドグランプリ", "car", 0xFFE07A2F.toInt(), 2, tags = listOf("レース")),
            GameTitle(3, "ほしふる庭園", null, 0xFF2E7D32.toInt(), 3, tags = listOf("放置")),
            GameTitle(4, "ドレミ横丁リズム部", "music", 0xFFC62828.toInt(), 4, tags = listOf("音ゲー", "毎日")),
            GameTitle(5, "砂時計の古城", "castle", 0xFF6D4C41.toInt(), 5, isArchived = true),
        )
        val created = ago(60L * 24 * 400)
        val accounts = listOf(
            GameAccount(1, 1, "メイン", loginHintMemo = "Google のアカウントでログイン", createdAt = created),
            GameAccount(2, 1, "サブ", createdAt = created),
            GameAccount(3, 2, "メイン", createdAt = created),
            GameAccount(4, 3, "メイン", createdAt = created),
            GameAccount(5, 4, "メイン", createdAt = created),
            GameAccount(6, 4, "サブ(放置中)", createdAt = created, isArchived = true),
            GameAccount(7, 5, "メイン", createdAt = created),
        )
        val staminas = listOf(
            StaminaResource(1, 1, "スタミナ", 240, 6, 200, ago(30)),
            StaminaResource(2, 2, "スタミナ", 240, 6, 236, ago(8)),
            StaminaResource(3, 3, "燃料", 100, 5, 100, ago(120)),
            StaminaResource(4, 4, "水やりの回数", 5, 120, 1, ago(30)),
            StaminaResource(5, 5, "ライフ", 10, 30, 3, ago(10)),
            StaminaResource(6, 7, "兵糧", 50, 10, 20, ago(30)),
        )
        val events = listOf(
            GameEvent(1, 1, "星降る夜の大型イベント", inHours(20)),
            GameEvent(2, 2, "シーズン3 ランキング", inHours(50)),
            GameEvent(3, 4, "新曲追加キャンペーン", inHours(24 * 9)),
            GameEvent(4, 1, "ログインボーナス週間", inHours(-30)),
            GameEvent(5, 1, "コラボ復刻", inHours(24 * 5), isCompleted = true),
        )
        val tasks = listOf(
            DailyTask(1, 1, "デイリーミッション", ResetType.DAILY, "05:00", 1),
            DailyTask(2, 1, "ギルドへの寄付", ResetType.DAILY, "05:00", 2),
            DailyTask(3, 2, "ログインだけ", ResetType.DAILY, "04:00", 1),
            DailyTask(4, 3, "週替わりカップに出走", ResetType.WEEKLY, "1 04:00", 1),
            DailyTask(5, 4, "水やり", ResetType.DAILY, "00:00", 1),
            DailyTask(6, 5, "フリープレイ 3 回", ResetType.DAILY, "05:00", 1),
        )
        // 今の期間にチェック済みの物と、年間の振り返りに使う過去のチェック
        val checkIns = buildList {
            for (id in listOf(2L, 5L)) {
                val task = tasks.first { it.id == id }
                add(TaskCheckIn(dailyTaskId = id, periodKey = TaskReset.periodKey(task, now, zone), isChecked = true, checkedAt = now))
            }
            for (d in 1L..90L) {
                add(TaskCheckIn(dailyTaskId = 1, periodKey = today.minusDays(d).toString(), isChecked = true, checkedAt = ago(d * 1440)))
                if (d % 3 != 0L) add(TaskCheckIn(dailyTaskId = 6, periodKey = today.minusDays(d).toString(), isChecked = true, checkedAt = ago(d * 1440)))
            }
        }.mapIndexed { i, c -> c.copy(id = i + 1L) }
        val gacha = buildList {
            val plan = listOf(
                Triple(0, 1, 3000), Triple(0, 3, 980), Triple(0, 5, 610),
                Triple(1, 1, 10000), Triple(1, 5, 1200),
                Triple(2, 3, 2400), Triple(3, 1, 4900), Triple(3, 2, 490),
                Triple(4, 5, 3060), Triple(5, 1, 12000), Triple(6, 3, 610),
                Triple(7, 1, 3000), Triple(8, 5, 1840), Triple(9, 1, 6100), Triple(10, 3, 1220), Triple(11, 1, 2440),
            )
            plan.forEachIndexed { i, (monthsAgo, account, yen) ->
                val date = today.minusMonths(monthsAgo.toLong()).withDayOfMonth(1).plusDays((i * 7L) % 25)
                    .let { if (it.isAfter(today)) today else it }
                add(
                    GachaLog(
                        id = i + 1L,
                        gameAccountId = account.toLong(),
                        date = date,
                        amount = yen,
                        currencyType = listOf("星晶石", "ジェム", null)[i % 3],
                        memo = if (i == 0) "限定ピックアップ。天井まであと 40 回" else null,
                        createdAt = now,
                    ),
                )
            }
        }.sortedByDescending { it.date }
        repo.replaceAll(
            Roster(titles, accounts, staminas, events, tasks, checkIns, gacha),
            AppSettings(
                themeColorMode = ThemeColorMode.MANUAL,
                manualThemeColor = 0xFF3F51B5.toInt(),
                autoBackupEnabled = true,
                lastAutoBackupAt = ago(60L * 24 * 2),
            ),
        )
        println("SEED titles=${titles.size} gacha=${gacha.size} checkIns=${checkIns.size}")
    }

    @Test
    fun 画面を撮る() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        seed(RosterRepository.get(app))
        val vm = RosterViewModel(app)
        // Robolectric では Room の最初の結果が届くのに時間がかかるので、最初から読み始めておく
        vm.viewModelScope.launch { vm.roster.collect {} }

        val screens = mapOf<String, @Composable () -> Unit>(
            "home" to { HomeScreen(vm, onOpenTitle = {}) },
            "gacha" to { GachaListScreen(vm, onEdit = {}) },
            "gachaEntry" to { GachaEntryScreen(vm, NEW_GACHA_ID, onDone = {}) },
            "stats" to { StatsScreen(vm, onOpenYearly = {}) },
            "yearly" to { YearlyScreen(vm, onBack = {}) },
            "settings" to { SettingsScreen(vm, onOpenBackup = {}, onOpenTitle = {}) },
            "backup" to { BackupScreen(vm, onBack = {}) },
            "detail" to { TitleDetailScreen(vm, titleId = 1L, onBack = {}) },
        )
        val longScreens = setOf("home", "stats", "settings", "backup", "detail")
        val indigo = 0xFF3F51B5.toInt()
        var dark by mutableStateOf(false)
        var name by mutableStateOf("")
        var seedColor by mutableStateOf<Int?>(indigo)
        var themeMode by mutableStateOf(ThemeColorMode.MANUAL)
        compose.setContent {
            AltRosterTheme(
                AppSettings(
                    themeColorMode = themeMode,
                    manualThemeColor = seedColor,
                    darkMode = if (dark) DarkMode.DARK else DarkMode.LIGHT,
                ),
            ) {
                // key で画面ごとに状態を作り直す(前の画面の remember を持ち越さない)
                key(name, seedColor) { screens[name]?.invoke() }
            }
        }
        fun settle(file: String) {
            // Room と DataStore は別のスレッドで読むので、記録と設定が届くまで待つ
            runCatching {
                compose.waitUntil(20_000) { vm.roster.value?.titles?.isNotEmpty() == true && vm.settings.value != null }
            }.onFailure { println("SHOT $file: 届かない roster=${vm.roster.value?.titles?.size} settings=${vm.settings.value}") }
            compose.mainClock.advanceTimeBy(1_000) // ゲージなどの動きを終わらせる
            compose.waitForIdle()
        }
        fun shoot(file: String) {
            settle(file)
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/$file.png")
        }
        for (n in screens.keys) for (d in listOf(false, true)) {
            val mode = if (d) "dark" else "light"
            dark = d
            name = n
            shoot("$n-$mode")
            if (n in longScreens) {
                repeat(3) { compose.onRoot().performTouchInput { swipeUp() } }
                shoot("$n-$mode-bottom")
            }
        }
        // ホームのタイトルのカードを開いた所
        for (d in listOf(false, true)) {
            dark = d
            // いったん空にしてから戻し、前の回の開いた状態とスクロールを持ち越さない
            name = ""
            compose.waitForIdle()
            name = "home"
            settle("home-expanded")

            compose.onNodeWithText("ほうき星クロニクル").performClick()
            compose.onRoot().performTouchInput { swipeUp() }
            shoot("home-expanded-${if (d) "dark" else "light"}")
        }
        // 既定の設定(壁紙の色に合わせる)。依頼者の端末(Android 12 以上)ではこちらの見た目になる
        themeMode = ThemeColorMode.DYNAMIC
        for (n in listOf("home", "stats")) for (d in listOf(false, true)) {
            dark = d
            // 前の回の開いた状態とスクロールを持ち越さない
            name = ""
            compose.waitForIdle()
            name = n
            shoot("$n-dynamic-${if (d) "dark" else "light"}")
        }
        themeMode = ThemeColorMode.MANUAL
        // 色の候補ごとの見え方(注意・満タンの色とぶつからないか)。null は「壁紙の色が無い端末で、色を選んでいない」とき
        name = "home"
        dark = false
        for ((label, c) in listOf("orange" to 0xFFE07A2F.toInt(), "rose" to 0xFFB3325B.toInt(), "fallback" to null)) {
            seedColor = c
            shoot("home-$label-light")
        }
    }
}
