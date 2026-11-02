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
import com.kakario.communityscrap.Post
import com.kakario.communityscrap.Site
import com.kakario.communityscrap.Store

@Composable
fun HomeScreen(
    feed: FeedViewModel,
    onOpen: (Post) -> Unit,
    onSettings: () -> Unit,
    onBookmarks: () -> Unit,
) {
    val ctx = LocalContext.current
    val sites = Site.entries.filter { it in Store.enabled }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (tab > sites.size) tab = 0
    val current: Site? = if (tab == 0) null else sites.getOrNull(tab - 1)

    val list: List<Post> = if (current == null) feed.merged() else feed.posts[current].orEmpty()
    val targetSites = if (current == null) sites else listOf(current)
    val anyLoading = targetSites.any { feed.loading[it] == true }

    Column(Modifier.fillMaxSize()) {
        Bar("커뮤 모아") {
            IconButton(onClick = onBookmarks) { Icon(Icons.Filled.BookmarkBorder, "북마크") }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "설정") }
        }

        val labels = listOf("전체") + sites.map { it.label }
        CategoryTabs(labels, tab, onSelect = { tab = it })

        val state = rememberLazyListState()
        LaunchedEffect(tab) { state.scrollToItem(0) }

        // 끝 근처까지 내리면 다음 페이지를 자동으로 불러온다
        val nearEnd by remember {
            derivedStateOf {
                val info = state.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
            }
        }
        LaunchedEffect(state, tab) {
            snapshotFlow { nearEnd }.collect { if (it && !feed.refreshing) feed.loadMore(targetSites) }
        }

        PullToRefreshBox(
            isRefreshing = feed.refreshing,
            onRefresh = { feed.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                val errs = targetSites.mapNotNull { s -> feed.errors[s]?.let { s to it } }
                items(errs, key = { "err_" + it.first.name }) { (s, msg) ->
                    ErrorCard(s, msg, onRetry = { feed.refreshSite(s) })
                }
                items(list, key = { it.key }) { p ->
                    PostRow(
                        p,
                        showSite = current == null,
                        onClick = { onOpen(p) },
                        onLong = {
                            val on = Store.toggleBookmark(p)
                            Toast.makeText(ctx, if (on) "북마크에 담았어요" else "북마크에서 뺐어요", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                item(key = "footer") {
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

@Composable
private fun ErrorCard(site: Site, msg: String, onRetry: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFFF4EE),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                SiteTag(site)
                Text(msg, fontSize = 13.sp, color = Color(0xFF6B4A3A))
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
                color = if (on) TabBlue else Color(0xFFEFF4FD),
                border = if (on) null else BorderStroke(1.dp, Color(0xFFD5E2F8)),
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
