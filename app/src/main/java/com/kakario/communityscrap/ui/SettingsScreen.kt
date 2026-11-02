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
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
                    CheckRow("글만 깔끔하게 보기", "글 화면에서 본문과 댓글까지만 보여줘요", Store.isolate) { Store.updateIsolate(it) }
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

            // 눈에 잘 띄는 게시판 고르기 버튼
            Surface(
                onClick = { picking = true },
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFE9F1FE),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC6DAFA)),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("보고 있는 게시판", fontSize = 12.sp, color = TabBlue)
                        Text(Store.boardName(site), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF14284B), maxLines = 1)
                    }
                    Text("바꾸기  ›", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TabBlue)
                }
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

/** 게시판 고르기: 전체 화면. 추천 → 사이트 구역별(취미·커뮤니티·게임 …) 목록, 검색, 주소 직접 입력 */
@Composable
private fun BoardPicker(site: Site, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf(Store.savedBoards(site)) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(Store.listUrl(site)) }
    var bestOnly by remember { mutableStateOf(Store.listUrl(site).contains("mode=best")) }
    val curUrl = Store.listUrl(site).substringBefore('?')

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

    // 열 때마다 사이트 메뉴를 새로 읽어 목록을 갱신(저장된 목록은 바로 보여줌)
    LaunchedEffect(site) {
        searching = true
        val fresh = runCatching { Boards.discover(ctx, site) }.getOrDefault(emptyList())
        if (fresh.isNotEmpty()) { found = fresh; Store.saveBoards(site, fresh) }
        searching = false
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            Column(Modifier.fillMaxSize()) {
                Bar("${site.label} 게시판 고르기", onBack = onClose)
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("게시판 이름 검색 (예: 피규어)") }, singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                )
                if (site == Site.ARCA) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp).clickable { bestOnly = !bestOnly },
                    ) {
                        Checkbox(checked = bestOnly, onCheckedChange = { bestOnly = it })
                        Text("채널은 개념글만 보기", fontSize = 14.sp)
                    }
                }
                if (searching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = TabBlue)
                }

                val preset = Boards.preset(site)
                val q = query.trim()
                fun match(b: Board) = q.isEmpty() || b.name.contains(q, ignoreCase = true) || b.section.contains(q)
                val groups: List<Pair<String, List<Board>>> = buildList {
                    preset.filter(::match).takeIf { it.isNotEmpty() }?.let { add("추천" to it) }
                    found.filter { f -> preset.none { it.url == f.url } && match(f) }
                        .groupBy { it.section.ifEmpty { "게시판" } }
                        .forEach { (sec, l) -> add(sec to l) }
                }

                LazyColumn(Modifier.weight(1f)) {
                    groups.forEach { (sec, boards) ->
                        item(key = "h_$sec") {
                            Text(
                                "$sec  ${boards.size}",
                                Modifier.fillMaxWidth().background(Color(0xFFF3F6FB)).padding(horizontal = 16.dp, vertical = 8.dp),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TabBlue,
                            )
                        }
                        items(boards, key = { sec + it.url }) { b ->
                            val cur = curUrl == b.url.substringBefore('?')
                            Row(
                                Modifier.fillMaxWidth().clickable { pick(b) }
                                    .background(if (cur) Color(0xFFE9F1FE) else Color.White)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    b.name, Modifier.weight(1f), fontSize = 16.sp,
                                    fontWeight = if (cur) FontWeight.Bold else FontWeight.Medium,
                                    color = if (cur) TabBlue else Color(0xFF1A1A1A),
                                )
                                if (cur) Text("보는 중", fontSize = 12.sp, color = TabBlue, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = Line, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                    item(key = "tail") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            if (searching) Text("사이트에서 게시판 목록을 불러오는 중이에요…", fontSize = 13.sp, color = Sub)
                            else if (found.isEmpty()) Text(
                                "사이트 게시판 목록을 못 불러왔어요. 로그인 후 다시 열어 보거나 주소를 직접 넣어 주세요.",
                                fontSize = 13.sp, color = Sub,
                            )
                            if (!manual) {
                                TextButton(onClick = { manual = true }) { Text("주소 직접 입력") }
                            } else {
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
