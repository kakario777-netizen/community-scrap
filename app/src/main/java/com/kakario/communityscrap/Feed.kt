package com.kakario.communityscrap

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 사이트별 목록을 모아 관리 */
class FeedViewModel(app: Application) : AndroidViewModel(app) {
    val posts = mutableStateMapOf<Site, List<Post>>()
    val loading = mutableStateMapOf<Site, Boolean>()
    val errors = mutableStateMapOf<Site, String>()
    private val pages = mutableMapOf<Site, Int>()
    private val usedUrl = mutableMapOf<Site, String>()

    var refreshing by mutableStateOf(false)
        private set

    init { refresh() }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            Store.enabled.map { site -> async { loadPage(site, 1) } }.awaitAll()
            Site.entries.filter { it !in Store.enabled }.forEach { posts.remove(it); errors.remove(it) }
            refreshing = false
        }
    }

    fun refreshSite(site: Site) {
        viewModelScope.launch { loadPage(site, 1) }
    }

    fun loadMore(sites: Collection<Site>) {
        sites.filter { it in Store.enabled && loading[it] != true && errors[it] == null }.forEach { site ->
            viewModelScope.launch { loadPage(site, (pages[site] ?: 1) + 1) }
        }
    }

    private suspend fun loadPage(site: Site, page: Int) {
        if (loading[site] == true) return
        loading[site] = true
        if (page == 1) errors.remove(site)
        val base = Store.listUrl(site)
        val url = site.pageUrl(base, page)
        val got = fetch(site, url)
        if (got == null) {
            if (page == 1) errors[site] = "불러오지 못했어요. 주소나 로그인 상태를 확인해 주세요."
        } else {
            val old = if (page == 1 || usedUrl[site] != base) emptyList() else posts[site].orEmpty()
            val seen = old.map { it.id }.toHashSet()
            val fresh = got.filter { seen.add(it.id) }
            posts[site] = old + fresh
            pages[site] = page
            usedUrl[site] = base
            if (page == 1 && got.isEmpty()) errors[site] = "글을 찾지 못했어요. 목록 주소를 확인해 주세요."
        }
        loading[site] = false
    }

    /** 먼저 바로 접속해 보고, 막히거나 글이 안 보이면 보이지 않는 웹뷰로 다시 연다 */
    private suspend fun fetch(site: Site, url: String): List<Post>? {
        val html = Net.getHtml(url)
        val direct = html?.let { h -> withContext(Dispatchers.Default) { runCatching { Parser.parse(site, h, url) }.getOrNull() } }
        if (!direct.isNullOrEmpty()) return direct
        val viaWeb = WebFetcher.load(getApplication(), url) { h ->
            runCatching { Parser.parse(site, h, url) }.getOrNull()
        }
        return viaWeb ?: direct
    }

    /** 전체 탭: 사이트를 번갈아 섞어서 보여준다 */
    fun merged(): List<Post> {
        val lists = Site.entries.filter { it in Store.enabled }.map { posts[it].orEmpty() }
        val out = ArrayList<Post>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) lists.forEach { l -> l.getOrNull(i)?.let(out::add) }
        return out
    }
}
