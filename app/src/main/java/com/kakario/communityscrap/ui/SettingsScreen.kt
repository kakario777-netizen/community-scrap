package com.kakario.communityscrap.ui

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import android.webkit.WebChromeClient
import com.kakario.communityscrap.Post
import com.kakario.communityscrap.Site
import com.kakario.communityscrap.Store

@Composable
fun SettingsScreen(onLogin: (Site) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    @Suppress("UNUSED_VARIABLE") val v = Store.loginVersion // 로그인 상태가 바뀌면 다시 그림

    Column(Modifier.fillMaxSize()) {
        Bar("설정", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "아래에서 한 번 로그인해 두면, 목록을 불러올 때와 글을 볼 때 그 로그인 상태가 그대로 쓰여요.",
                fontSize = 13.sp, color = Sub, lineHeight = 19.sp,
            )
            Site.entries.forEach { site -> SiteCard(site, onLogin) }
        }
    }
}

@Composable
private fun SiteCard(site: Site, onLogin: (Site) -> Unit) {
    val ctx = LocalContext.current
    val on = site in Store.enabled
    val logged = Store.hasLogin(site)
    var url by remember(site, Store.listUrls) { mutableStateOf(Store.listUrl(site)) }

    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF7F7F7)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(site.label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = site.c())
                Text(if (on) "목록에 표시" else "숨김", fontSize = 12.sp, color = Sub)
                Spacer(Modifier.padding(4.dp))
                Switch(checked = on, onCheckedChange = { Store.setEnabled(site, it) })
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (logged) "로그인 저장됨" else "로그인 안 됨",
                    Modifier.weight(1f),
                    fontSize = 14.sp,
                    color = if (logged) Color(0xFF2E7D32) else Sub,
                )
                if (logged) {
                    OutlinedButton(onClick = {
                        Store.logout(site)
                        Toast.makeText(ctx, "${site.label} 로그아웃했어요", Toast.LENGTH_SHORT).show()
                    }) { Text("로그아웃") }
                }
                Button(onClick = { onLogin(site) }) { Text(if (logged) "다시 로그인" else "로그인") }
            }

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("가져올 게시판 주소") },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    Store.setListUrl(site, url)
                    Toast.makeText(ctx, "저장했어요", Toast.LENGTH_SHORT).show()
                }) { Text("주소 저장") }
                TextButton(onClick = {
                    url = site.defaultList
                    Store.setListUrl(site, site.defaultList)
                }) { Text("기본값으로") }
            }
        }
    }
}

@Composable
fun LoginScreen(site: Site, onDone: () -> Unit) {
    var progress by remember { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }
    val finish = {
        CookieManager.getInstance().flush()
        Store.markLogin(site, true)
        onDone()
    }
    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else finish()
    }
    Column(Modifier.fillMaxSize()) {
        Bar("${site.label} 로그인", onBack = finish) {
            TextButton(onClick = finish) { Text("완료", fontWeight = FontWeight.Bold) }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        Text(
            "로그인을 마친 뒤 오른쪽 위 '완료'를 눌러 주세요.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 12.sp, color = Sub,
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    setupCommon()
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            CookieManager.getInstance().flush()
                        }
                    }
                    loadUrl(site.loginUrl)
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

@Composable
fun BookmarksScreen(onOpen: (Post) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Bar("북마크", onBack = onBack)
        if (Store.bookmarks.isEmpty()) {
            EmptyNote("글을 길게 누르면 북마크에 담겨요")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(Store.bookmarks, key = { it.url }) { p ->
                    PostRow(p, showSite = true, onClick = { onOpen(p) }, onLong = {
                        Store.toggleBookmark(p)
                        Toast.makeText(ctx, "북마크에서 뺐어요", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}
