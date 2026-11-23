package com.kakario.communityscrap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.toArgb
import com.kakario.communityscrap.ui.Pal
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kakario.communityscrap.ui.BookmarksScreen
import com.kakario.communityscrap.ui.HomeScreen
import com.kakario.communityscrap.ui.LoginScreen
import com.kakario.communityscrap.ui.SettingsScreen
import com.kakario.communityscrap.ui.ViewerScreen

/** 화면 사이에 넘겨줄 지금 보는 글 */
object Current {
    var post: Post? = null
    /** 이전·다음 글로 넘길 때 쓰는, 글을 연 목록 */
    var list: List<Post> = emptyList()
}

val Accent = Color(0xFF1E6BE6)

/** 모든 글자 스타일에 고른 글꼴 적용 */
fun typographyWith(f: FontFamily): Typography {
    val t = Typography()
    if (f == FontFamily.Default) return t
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f), headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f), titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f),
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val updater = com.kakario.communityscrap.update.Updater.get(applicationContext)
        if (savedInstanceState == null) {
            updater.check(quiet = true)
            // 업데이트 완료 알림을 띄우려면 알림 허용이 필요 (안드로이드 13+)
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        setContent {
            val pal = Pal.cur
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.SideEffect {
                val c = androidx.core.view.WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !pal.dark
                c.isAppearanceLightNavigationBars = !pal.dark
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(pal.bg.toArgb()))
                @Suppress("DEPRECATION")
                window.navigationBarColor = pal.bg.toArgb()
            }
            val base = if (pal.dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(
                colorScheme = base.copy(
                    primary = Accent,
                    onPrimary = Color.White,
                    secondaryContainer = pal.soft,
                    onSecondaryContainer = pal.text,
                    background = pal.bg,
                    onBackground = pal.text,
                    surface = pal.bg,
                    onSurface = pal.text,
                    onSurfaceVariant = pal.sub,
                    surfaceContainer = pal.card,
                    surfaceContainerHigh = pal.card,
                    surfaceContainerHighest = pal.card,
                    outline = pal.line,
                    outlineVariant = pal.line,
                ),
                typography = androidx.compose.runtime.remember(Store.font) { typographyWith(Fonts.family(this@MainActivity, Store.font)) },
            ) {
                Surface(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                    color = pal.bg,
                ) {
                    val updateState by updater.state.collectAsState()
                    com.kakario.communityscrap.ui.UpdateDialog(updateState, updater)
                    val nav = rememberNavController()
                    val feed: FeedViewModel = viewModel()
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                feed = feed,
                                onOpen = { p, list -> Current.post = p; Current.list = list; Store.markRead(p); nav.navigate("viewer") },
                                onSettings = { nav.navigate("settings") },
                                onBookmarks = { nav.navigate("bookmarks") },
                            )
                        }
                        composable("viewer") {
                            ViewerScreen(Current.post, Current.list, onBack = { nav.popBackStack() })
                        }
                        composable("bookmarks") {
                            BookmarksScreen(
                                onOpen = { p -> Current.post = p; Current.list = Store.bookmarks; Store.markRead(p); nav.navigate("viewer") },
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onLogin = { s -> nav.navigate("login/${s.name}") },
                                onBack = { feed.refresh(); nav.popBackStack() },
                            )
                        }
                        composable("login/{site}") { e ->
                            val site = Site.valueOf(e.arguments?.getString("site") ?: Site.ARCA.name)
                            LoginScreen(site, onDone = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
