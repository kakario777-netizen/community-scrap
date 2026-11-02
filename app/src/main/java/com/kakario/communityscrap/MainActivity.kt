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
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
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
}

val Accent = Color(0xFFE8590C)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    secondaryContainer = Color(0xFFFFE8DA),
                    onSecondaryContainer = Color(0xFF5A2200),
                    background = Color.White,
                    surface = Color.White,
                    surfaceContainer = Color(0xFFF6F6F6),
                    surfaceContainerHigh = Color(0xFFF1F1F1),
                )
            ) {
                Surface(
                    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
                    color = Color.White,
                ) {
                    val nav = rememberNavController()
                    val feed: FeedViewModel = viewModel()
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                feed = feed,
                                onOpen = { p -> Current.post = p; Store.markRead(p); nav.navigate("viewer") },
                                onSettings = { nav.navigate("settings") },
                                onBookmarks = { nav.navigate("bookmarks") },
                            )
                        }
                        composable("viewer") {
                            ViewerScreen(Current.post, onBack = { nav.popBackStack() })
                        }
                        composable("bookmarks") {
                            BookmarksScreen(
                                onOpen = { p -> Current.post = p; Store.markRead(p); nav.navigate("viewer") },
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
