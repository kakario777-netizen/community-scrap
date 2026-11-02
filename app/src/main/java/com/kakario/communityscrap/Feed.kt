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
    /** 게시판 소분류 목록과 지금 고른 소분류 주소 */
    val categories = mutableStateMapOf<Site, List<Category>>()
    val selectedCat = mutableStateMapOf<Site, String>()
    private val catBoard = mutableMapOf<Site, String>()
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

    fun selectCategory(site: Site, cat: Category?) {
        if (cat == null) selectedCat.remove(site) else selectedCat[site] = cat.url
        posts.remove(site)
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
        val board = Store.listUrl(site)
        // 설정에서 게시판을 바꿨으면 소분류도 새로
        if (catBoard[site] != null && catBoard[site] != board) {
            categories.remove(site); selectedCat.remove(site); catBoard.remove(site)
        }
        val base = selectedCat[site] ?: board
        val url = site.pageUrl(base, page)
        val got = fetch(site, url, wantCats = page == 1)
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
    private suspend fun fetch(site: Site, url: String, wantCats: Boolean): List<Post>? {
        var page: String? = null
        val html = Net.getHtml(url)
        var result = html?.let { h -> withContext(Dispatchers.Default) { runCatching { Parser.parse(site, h, url) }.getOrNull() } }
        if (!result.isNullOrEmpty()) page = html
        else {
            val viaWeb = WebFetcher.load(getApplication(), url) { h ->
                runCatching { Parser.parse(site, h, url) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { h to it }
            }
            if (viaWeb != null) { page = viaWeb.first; result = viaWeb.second }
        }
        val pg = page
        if (wantCats && pg != null) {
            val board = Store.listUrl(site)
            val cats = withContext(Dispatchers.Default) {
                runCatching { Parser.categories(site, pg, board) }.getOrDefault(emptyList())
            }
            // 소분류를 고른 상태에선 목록이 비어 오면 기존 것을 유지
            if (cats.isNotEmpty() || selectedCat[site] == null) categories[site] = cats
            catBoard[site] = board
        }
        return result
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
