@file:OptIn(ExperimentalMaterial3Api::class)

package com.kakario.communityscrap.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakario.communityscrap.FeedViewModel
import com.kakario.communityscrap.Category
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import com.kakario.communityscrap.Post
import com.kakario.communityscrap.Site
import com.kakario.communityscrap.Store

@Composable
fun HomeScreen(
    feed: FeedViewModel,
    onOpen: (Post, List<Post>) -> Unit,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit,
) {
    val ctx = LocalContext.current
    val sites = Site.entries.filter { it in Store.enabled }
    val tabs = Store.visibleTabs()
    var tabKey by rememberSaveable { mutableStateOf(Store.startTab) }
    if (tabKey !in tabs) tabKey = if (Store.startTab in tabs) Store.startTab else tabs.first()
    val current: Site? = if (tabKey == "ALL") null else runCatching { Site.valueOf(tabKey) }.getOrNull()
    val tab = tabs.indexOf(tabKey).coerceAtLeast(0)

    val isAll = current == null
    val list: List<Post> = if (current == null) feed.merged() else feed.paged[current].orEmpty()
    val targetSites = if (current == null) sites else listOf(current)
    val anyLoading = if (current == null) targetSites.any { feed.loading[it] == true } else feed.pageLoading[current] == true
    val curPage = current?.let { feed.pageNo[it] } ?: 1

    // 사이트 탭은 번호 페이지 목록을 쓴다
    LaunchedEffect(current, current?.let { Store.listUrl(it) }) { current?.let { feed.ensurePaged(it) } }

    Column(Modifier.fillMaxSize()) {
        Bar("커뮤 모아") {
            IconButton(onClick = onBookmarks) { Icon(Icons.Filled.BookmarkBorder, "북마크") }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "설정") }
        }

        val labels = tabs.map { k -> if (k == "ALL") "전체" else Site.valueOf(k).label }
        CategoryTabs(labels, tab, onSelect = { tabKey = tabs[it] })
        if (current != null) {
            val cats = feed.categories[current].orEmpty()
            if (cats.isNotEmpty()) {
                SubCategoryRow(cats, feed.selectedCat[current], onSelect = { feed.selectCategory(current, it) })
            }
        }

        val state = rememberLazyListState()
        LaunchedEffect(tab, current?.let { feed.selectedCat[it] }, curPage) { state.scrollToItem(0) }

        // 전체 탭: 끝 근처까지 내리면 다음 페이지를 자동으로 불러온다
        val nearEnd by remember {
            derivedStateOf {
                val info = state.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
            }
        }
        LaunchedEffect(state, tab) {
            if (isAll) snapshotFlow { nearEnd }.collect { if (it && !feed.refreshing) feed.loadMore(targetSites) }
        }

        // 목록을 좌우로 밀면 옆 탭으로 넘어간다
        val swipeScope = rememberCoroutineScope()
        val dragX = remember { Animatable(0f) }
        val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
        PullToRefreshBox(
            isRefreshing = if (isAll) feed.refreshing else false,
            onRefresh = { if (current == null) feed.refresh() else feed.openPage(current, curPage) },
            modifier = Modifier.fillMaxSize()
                .pointerInput(tabs, tab) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { change, dx ->
                            total += dx
                            // 끝 탭에서 더 밀면 살짝만 움직인다
                            val atEdge = (total > 0 && tab == 0) || (total < 0 && tab == tabs.size - 1)
                            swipeScope.launch { dragX.snapTo(if (atEdge) total * 0.15f else total * 0.6f) }
                            change.consume()
                        },
                        onDragEnd = {
                            val goTo = when {
                                total < -width * 0.22f && tab < tabs.size - 1 -> tab + 1
                                total > width * 0.22f && tab > 0 -> tab - 1
                                else -> null
                            }
                            swipeScope.launch {
                                if (goTo != null) {
                                    dragX.animateTo(if (goTo > tab) -width * 0.35f else width * 0.35f, tween(110))
                                    tabKey = tabs[goTo]
                                    dragX.snapTo(if (goTo > tab) width * 0.25f else -width * 0.25f)
                                }
                                dragX.animateTo(0f, tween(180))
                            }
                        },
                        onDragCancel = { swipeScope.launch { dragX.animateTo(0f) } },
                    )
                }
                .graphicsLayer {
                    translationX = dragX.value
                    alpha = 1f - (kotlin.math.abs(dragX.value) / width).coerceIn(0f, 0.5f)
                },
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                val errs = if (current == null) targetSites.mapNotNull { s -> feed.errors[s]?.let { s to it } }
                else listOfNotNull(feed.pageError[current]?.let { current to it })
                items(errs, key = { "err_" + it.first.name }) { (s, msg) ->
                    ErrorCard(s, msg, onRetry = { if (current == null) feed.refreshSite(s) else feed.openPage(s, curPage) })
                }
                if (current != null && anyLoading && list.isNotEmpty()) {
                    item(key = "toploading") {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = TabBlue)
                    }
                }
                items(list, key = { it.key }) { p ->
                    PostRow(
                        p,
                        showSite = current == null,
                        onClick = { onOpen(p, list) },
                        onLong = {
                            val on = Store.toggleBookmark(p)
                            Toast.makeText(ctx, if (on) "북마크에 담았어요" else "북마크에서 뺐어요", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                item(key = "footer") {
                    if (current != null) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (anyLoading && list.isEmpty()) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            PagerBar(curPage, enabled = !anyLoading, onGo = { feed.openPage(current, it) })
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                            when {
                                anyLoading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                list.isEmpty() && errs.isEmpty() -> Text("당겨서 새로고침", color = Sub, fontSize = 13.sp)
                                list.isNotEmpty() -> TextButton(onClick = { feed.loadMore(targetSites) }) { Text("더 보기") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 페이지 번호 줄: ‹ 1 2 3 4 5 › */
@Composable
fun PagerBar(cur: Int, enabled: Boolean, onGo: (Int) -> Unit) {
    val start = ((cur - 1) / 5) * 5 + 1
    Row(
        Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PageBtn("‹", on = false, enabled = enabled && cur > 1) { onGo(cur - 1) }
        for (n in start until start + 5) {
            PageBtn(n.toString(), on = n == cur, enabled = enabled) { onGo(n) }
        }
        PageBtn("›", on = false, enabled = enabled) { onGo(cur + 1) }
    }
    Text("$cur 페이지", Modifier.padding(top = 6.dp), fontSize = 11.sp, color = Sub)
}

@Composable
private fun PageBtn(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = { if (enabled && !on) onClick() },
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (on) TabBlue else Pal.cur.soft,
        enabled = enabled || on,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = if (label.length == 1 && !label[0].isDigit()) 20.sp else 14.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    on -> Color.White
                    enabled -> TabBlue
                    else -> Pal.cur.sub.copy(alpha = 0.5f)
                },
            )
        }
    }
}

@Composable
private fun ErrorCard(site: Site, msg: String, onRetry: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = Pal.cur.card,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                SiteTag(site)
                Text(msg, fontSize = 13.sp, color = Pal.cur.text)
            }
            TextButton(onClick = onRetry) { Text("다시") }
        }
    }
}

val TabBlue = Color(0xFF1E6BE6)

/** 칸으로 나뉜 파란 분류 탭. 글자는 칸 안에 한 줄로 맞춘다 */
@Composable
fun CategoryTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Surface(
                onClick = { onSelect(i) },
                modifier = Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(9.dp),
                color = if (on) TabBlue else Pal.cur.soft,
                border = if (on) null else BorderStroke(1.dp, Pal.cur.softBorder),
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    FitText(
                        l,
                        color = if (on) Color.White else TabBlue,
                        bold = on,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = Line)
}

/** 칸 너비에 맞춰 글자 크기를 줄여 한 줄에 딱 들어가게 */
@Composable
fun FitText(text: String, color: Color, bold: Boolean, maxSize: Float = 14.5f, minSize: Float = 10f) {
    var size by remember(text) { mutableFloatStateOf(maxSize) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(
        text,
        color = color,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r ->
            if (r.didOverflowWidth && size > minSize) size -= 0.5f else ready = true
        },
    )
}

/** 게시판 소분류: 가로로 밀어서 고르는 줄 */
@Composable
fun SubCategoryRow(cats: List<Category>, selectedUrl: String?, onSelect: (Category?) -> Unit) {
    val all = listOf<Category?>(null) + cats
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(all, key = { it?.url ?: "all" }) { c ->
            val on = if (c == null) selectedUrl == null else c.url == selectedUrl
            Surface(
                onClick = { onSelect(c) },
                shape = RoundedCornerShape(50),
                color = if (on) Pal.cur.soft else Pal.cur.bg,
                border = BorderStroke(1.dp, if (on) TabBlue else Pal.cur.line),
            ) {
                Text(
                    c?.name ?: "전체",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 13.sp,
                    color = if (on) TabBlue else Pal.cur.text,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
    HorizontalDivider(color = Line)
}
