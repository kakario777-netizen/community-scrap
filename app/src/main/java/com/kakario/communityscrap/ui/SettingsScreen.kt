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
import com.kakario.communityscrap.Board
import com.kakario.communityscrap.Boards
import com.kakario.communityscrap.Fonts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.kakario.communityscrap.Site
import com.kakario.communityscrap.Store

@Composable
fun SettingsScreen(onLogin: (Site) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    @Suppress("UNUSED_VARIABLE") val v = Store.loginVersion // 로그인 상태가 바뀌면 다시 그림
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Bar("설정", onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("보기")
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF7F7F7)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    CheckRow("광고 차단", "글 화면의 광고를 막고 광고 칸을 숨겨요", Store.adBlock) { Store.updateAdBlock(it) }
                    CheckRow("저장 막힘 풀기", "이미지·영상을 꾹 눌러 저장할 수 있게 해요", Store.unlock) { Store.updateUnlock(it) }
                    CheckRow("글 본문에도 글꼴 적용", "아래에서 고른 글꼴을 글 화면에도 써요", Store.fontInWeb) { Store.updateFontInWeb(it) }
                }
            }

            SectionTitle("글꼴")
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF7F7F7)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    val fonts = Fonts.available(ctx)
                    fonts.forEach { f ->
                        val fam = remember(f.key) { Fonts.family(ctx, f.key) }
                        Row(
                            Modifier.fillMaxWidth().clickable { Store.updateFont(f.key) }.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = Store.font == f.key, onClick = { Store.updateFont(f.key) })
                            Column(Modifier.weight(1f)) {
                                Text(f.label, fontSize = 15.sp, fontFamily = fam, fontWeight = FontWeight.SemiBold)
                                Text("가나다 커뮤니티 인기글 모아보기 123", fontSize = 13.sp, fontFamily = fam, color = Sub)
                            }
                        }
                    }
                    if (fonts.size <= 1) {
                        Text(
                            "추가 글꼴을 불러오지 못했어요. 다음 업데이트에서 다시 들어가요.",
                            Modifier.padding(12.dp), fontSize = 12.sp, color = Sub,
                        )
                    }
                }
            }

            SectionTitle("사이트")
            Text(
                "로그인을 한 번 해 두면, 목록을 불러올 때와 글을 볼 때 그 로그인 상태가 그대로 쓰여요.",
                fontSize = 13.sp, color = Sub, lineHeight = 19.sp,
            )
            Site.entries.forEach { site -> SiteCard(site, onLogin) }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TabBlue, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun CheckRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 12.sp, color = Sub)
        }
    }
}

@Composable
private fun SiteCard(site: Site, onLogin: (Site) -> Unit) {
    val ctx = LocalContext.current
    val on = site in Store.enabled
    val logged = Store.hasLogin(site)
    var picking by remember { mutableStateOf(false) }

    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF7F7F7)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(site.label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = site.c())
                Text(if (on) "목록에 표시" else "숨김", fontSize = 12.sp, color = Sub)
                Spacer(Modifier.padding(4.dp))
                Switch(checked = on, onCheckedChange = { Store.setEnabled(site, it) })
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("게시판", fontSize = 12.sp, color = Sub)
                    Text(Store.boardName(site), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                OutlinedButton(onClick = { picking = true }) { Text("게시판 선택") }
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
        }
    }

    if (picking) BoardPicker(site, onClose = { picking = false })
}

/** 게시판 고르기 창: 자주 쓰는 게시판 + 사이트에서 찾은 게시판 + 주소 직접 입력 */
@Composable
private fun BoardPicker(site: Site, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<Board>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(Store.listUrl(site)) }
    var bestOnly by remember { mutableStateOf(Store.listUrl(site).contains("mode=best")) }

    fun pick(b: Board) {
        var u = b.url
        var name = b.name
        if (site == Site.ARCA && bestOnly && b.url != "https://arca.live/b/live") {
            u = b.url.substringBefore('?') + "?mode=best"; name = "$name (개념글)"
        }
        Store.setBoard(site, name, u)
        Toast.makeText(ctx, "'$name' 게시판으로 바꿨어요", Toast.LENGTH_SHORT).show()
        onClose()
    }

    fun search() {
        searching = true
        scope.launch {
            found = Boards.discover(ctx, site)
            searching = false
            searched = true
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${site.label} 게시판 선택") },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("게시판 이름 검색") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (site == Site.ARCA) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { bestOnly = !bestOnly }) {
                        Checkbox(checked = bestOnly, onCheckedChange = { bestOnly = it })
                        Text("채널은 개념글만 보기", fontSize = 13.sp)
                    }
                }
                val preset = Boards.preset(site)
                val extra = found.filter { f -> preset.none { it.url == f.url } }
                val q = query.trim()
                val shown = (preset + extra).filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(shown, key = { it.url }) { b ->
                        val cur = Store.listUrl(site).substringBefore('?') == b.url.substringBefore('?')
                        Row(
                            Modifier.fillMaxWidth().clickable { pick(b) }.padding(vertical = 11.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                b.name, Modifier.weight(1f), fontSize = 15.sp,
                                fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal,
                                color = if (cur) TabBlue else Color(0xFF1A1A1A),
                            )
                            if (b in preset) Text("추천", fontSize = 11.sp, color = Sub)
                        }
                        HorizontalDivider(color = Line)
                    }
                    item {
                        when {
                            searching -> Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                            }
                            !searched -> TextButton(onClick = { search() }, modifier = Modifier.fillMaxWidth()) {
                                Text("사이트에서 게시판 더 찾기")
                            }
                            extra.isEmpty() -> Text(
                                "더 찾은 게시판이 없어요. 로그인 후 다시 해 보거나 주소를 직접 넣어 주세요.",
                                Modifier.padding(8.dp), fontSize = 12.sp, color = Sub,
                            )
                        }
                    }
                    item {
                        if (!manual) {
                            TextButton(onClick = { manual = true }, modifier = Modifier.fillMaxWidth()) { Text("주소 직접 입력") }
                        } else {
                            Column(Modifier.padding(top = 6.dp)) {
                                OutlinedTextField(
                                    value = url, onValueChange = { url = it },
                                    label = { Text("게시판 주소") }, singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(onClick = {
                                    Store.setListUrl(site, url)
                                    Toast.makeText(ctx, "저장했어요", Toast.LENGTH_SHORT).show()
                                    onClose()
                                }) { Text("이 주소로 저장") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
    )
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
