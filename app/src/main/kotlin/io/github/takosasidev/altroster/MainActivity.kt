package io.github.takosasidev.altroster

import android.view.ViewGroup
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.takosasidev.altroster.data.backup.AutoBackup
import io.github.takosasidev.altroster.notify.AlarmScheduler
import io.github.takosasidev.altroster.notify.Notifier
import io.github.takosasidev.altroster.ui.AppLockGate
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
import io.github.takosasidev.altroster.widget.RosterWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 画面の行き先の名前。ショートカットからの行き先の指定もここに置く。 */
object Routes {
    const val HOME = "home"
    const val GACHA = "gacha"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val YEARLY = "yearly"
    const val BACKUP = "backup"
    const val TITLE = "title/{id}"
    const val GACHA_ENTRY = "gachaEntry/{id}"

    fun title(id: Long) = "title/$id"
    fun gachaEntry(id: Long = NEW_GACHA_ID) = "gachaEntry/$id"

    /** ショートカット(res/xml/shortcuts.xml)が Intent に付ける行き先。 */
    const val EXTRA_DESTINATION = "io.github.takosasidev.altroster.DESTINATION"
    const val DEST_GACHA_ENTRY = "gacha_entry"
}

class MainActivity : ComponentActivity() {

    /**
     * ショートカットから渡された Intent。
     * setIntent() は画面の描き直しを起こさないので、状態として持ち、処理したら null に戻す
     * (アプリが開いたままショートカットを押した時にも進めるように)。
     */
    private val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 8.1(API 27)までは、OS が画面の最初の入力欄へ勝手にフォーカスを渡し、キーボードが出る。
        // 入れ物の側でフォーカスを受けて、入力欄には押した時だけ渡す
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            findViewById<ViewGroup>(android.R.id.content).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            }
        }
        pendingIntent.value = intent

        // 開いた時の確かめ(自動のバックアップ、アラームの仕込み直し、ウィジェットの更新)は
        // IO のスレッドに投げて、画面の表示を待たせない。失敗しても、アプリは開く。
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AutoBackup.runIfDue(applicationContext) }
            runCatching { AlarmScheduler.arm(applicationContext) }
            runCatching { RosterWidget.refresh(applicationContext) }
        }

        setContent {
            val vm: RosterViewModel = viewModel()
            // 設定が読めるまでは何も出さない(既定の色やロック無しの画面が一瞬見えるのを防ぐ)
            val settings = vm.settings.collectAsStateWithLifecycle().value ?: return@setContent
            AltRosterTheme(settings) {
                AppLockGate(enabled = settings.appLockEnabled) {
                    val navController = rememberNavController()
                    AppShell(vm, navController)
                    HandleLaunchIntent(navController, pendingIntent.value) { pendingIntent.value = null }
                    AskNotificationOnce()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }
}

private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "ホーム", Icons.Filled.Home),
    GACHA(Routes.GACHA, "課金", Icons.Filled.Payments),
    STATS(Routes.STATS, "集計", Icons.Filled.BarChart),
    SETTINGS(Routes.SETTINGS, "設定", Icons.Filled.Settings),
}

@Composable
private fun AppShell(vm: RosterViewModel, navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onTopLevel = TopLevel.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            // 掘り下げた先の画面では下のナビを畳む(戻る操作とタブの切り替えが混ざらないように)
            AnimatedVisibility(visible = onTopLevel, enter = expandVertically(), exit = shrinkVertically()) {
                NavigationBar {
                    TopLevel.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                if (currentRoute != dest.route && backStackEntry?.isFront() == true) {
                                    navController.navigate(dest.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // 外側で空けた分を「使用済み」と伝えないと、各画面の Scaffold がもう一度同じ余白を足す
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(150)) },
        ) {
            composable(Routes.HOME) { entry ->
                HomeScreen(vm, onOpenTitle = { id -> navController.navigateOnce(entry, Routes.title(id)) })
            }
            composable(Routes.GACHA) { entry ->
                GachaListScreen(vm, onEdit = { id -> navController.navigateOnce(entry, Routes.gachaEntry(id)) })
            }
            composable(Routes.STATS) { entry ->
                StatsScreen(vm, onOpenYearly = { navController.navigateOnce(entry, Routes.YEARLY) })
            }
            composable(Routes.SETTINGS) { entry ->
                SettingsScreen(
                    vm,
                    onOpenBackup = { navController.navigateOnce(entry, Routes.BACKUP) },
                    onOpenTitle = { id -> navController.navigateOnce(entry, Routes.title(id)) },
                )
            }
            composable(Routes.YEARLY) { entry -> YearlyScreen(vm, onBack = { navController.backOnce(entry) }) }
            composable(Routes.BACKUP) { entry -> BackupScreen(vm, onBack = { navController.backOnce(entry) }) }
            composable(Routes.TITLE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                TitleDetailRoute(vm, entry, navController)
            }
            composable(Routes.GACHA_ENTRY, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                GachaEntryScreen(
                    vm,
                    logId = entry.arguments?.getLong("id") ?: NEW_GACHA_ID,
                    onDone = { navController.backOnce(entry) },
                )
            }
        }
    }
}

@Composable
private fun TitleDetailRoute(vm: RosterViewModel, entry: NavBackStackEntry, navController: NavHostController) {
    io.github.takosasidev.altroster.ui.title.TitleDetailScreen(
        vm,
        titleId = entry.arguments?.getLong("id") ?: 0L,
        onBack = { navController.backOnce(entry) },
    )
}

/** ショートカットから開かれたときの行き先を処理する。 */
@Composable
private fun HandleLaunchIntent(navController: NavHostController, intent: Intent?, onHandled: () -> Unit) {
    LaunchedEffect(intent) {
        if (intent == null) return@LaunchedEffect
        if (intent.getStringExtra(Routes.EXTRA_DESTINATION) == Routes.DEST_GACHA_ENTRY) {
            navController.navigate(Routes.gachaEntry()) { launchSingleTop = true }
        }
        // 同じ Intent で 2 回進まないよう、処理したら捨てる
        onHandled()
    }
}

/**
 * Android 13 以降は、通知を出すのに利用者の許可が要る。初めて開いた時に 1 回だけ聞く。
 * 断られたあとは、設定の画面の「通知」からやり直せる。
 */
@Composable
private fun AskNotificationOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("first_run", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("askedNotifications", false) && !Notifier.canPost(context)) {
            prefs.edit().putBoolean("askedNotifications", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** その画面がまだ前に出ているか。画面が切り替わる途中に届いた 2 回目の操作を捨てるのに使う。 */
private fun NavBackStackEntry.isFront(): Boolean = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

private fun NavHostController.navigateOnce(from: NavBackStackEntry, route: String) {
    if (from.isFront()) navigate(route)
}

/** 戻る。切り替えの途中の 2 回目の操作を捨て、戻る先が無いときは何もしない(画面が空にならないように)。 */
private fun NavHostController.backOnce(from: NavBackStackEntry) {
    if (from.isFront() && previousBackStackEntry != null) popBackStack()
}
