package com.kakario.communityscrap

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** 앱 설정·읽은 글·북마크 저장 */
object Store {
    private lateinit var prefs: SharedPreferences

    var readKeys by mutableStateOf(emptySet<String>())
        private set
    var bookmarks by mutableStateOf(emptyList<Post>())
        private set
    var enabled by mutableStateOf(Site.entries.toSet())
        private set
    var listUrls by mutableStateOf(emptyMap<Site, String>())
        private set
    /** 로그인 화면을 다녀오면 숫자를 올려 상태 표시를 새로 그리게 한다 */
    var loginVersion by mutableStateOf(0)

    /** 고른 게시판 이름(주소별) */
    var boardNames by mutableStateOf(emptyMap<Site, String>())
        private set

    /** 글꼴: system, nanumgothic, gowundodum, nanummyeongjo */
    var font by mutableStateOf("system")
        private set
    /** 광고 차단 (기본 켜짐) */
    var adBlock by mutableStateOf(true)
        private set
    /** 다운로드·복사 막힌 것 풀기 (기본 켜짐) */
    var unlock by mutableStateOf(true)
        private set
    /** 글 본문에도 고른 글꼴 쓰기 */
    var fontInWeb by mutableStateOf(true)
        private set

    /** 글 화면에서 본문·댓글만 보이기 (기본 켜짐) */
    var isolate by mutableStateOf(true)
        private set
    fun updateIsolate(v: Boolean) { isolate = v; prefs.edit().putBoolean("isolate", v).apply() }

    /** 배경색 */
    var bgKey by mutableStateOf("white")
        private set
    fun updateBg(v: String) { bgKey = v; prefs.edit().putString("bg", v).apply() }

    /** 탭 순서(ALL=전체, 그 외 사이트 이름), 전체 탭 켜기, 앱 켰을 때 첫 탭 */
    var tabOrder by mutableStateOf(listOf("ALL") + Site.entries.map { it.name })
        private set
    var allTabOn by mutableStateOf(true)
        private set
    var startTab by mutableStateOf("ALL")
        private set

    fun moveTab(key: String, delta: Int) {
        val l = tabOrder.toMutableList()
        val i = l.indexOf(key); val j = i + delta
        if (i < 0 || j !in l.indices) return
        l[i] = l[j]; l[j] = key
        tabOrder = l
        prefs.edit().putString("tab_order", l.joinToString(",")).apply()
    }
    fun updateAllTab(v: Boolean) { allTabOn = v; prefs.edit().putBoolean("all_tab", v).apply() }
    fun updateStartTab(v: String) { startTab = v; prefs.edit().putString("start_tab", v).apply() }

    /** 보이는 탭 순서대로 */
    fun visibleTabs(): List<String> = tabOrder.filter { k ->
        if (k == "ALL") allTabOn else runCatching { Site.valueOf(k) }.getOrNull()?.let { it in enabled } == true
    }.ifEmpty { listOf("ALL") }

    /** 이미지·영상 저장 폴더 (비어 있으면 기본: 사진/동영상의 커뮤모아 폴더) */
    var saveTree by mutableStateOf<String?>(null)
        private set
    fun updateSaveTree(v: String?) {
        saveTree = v
        prefs.edit().apply { if (v == null) remove("save_tree") else putString("save_tree", v) }.apply()
    }

    fun updateFont(v: String) { font = v; prefs.edit().putString("font", v).apply() }
    fun updateAdBlock(v: Boolean) { adBlock = v; prefs.edit().putBoolean("adblock", v).apply() }
    fun updateUnlock(v: Boolean) { unlock = v; prefs.edit().putBoolean("unlock", v).apply() }
    fun updateFontInWeb(v: Boolean) { fontInWeb = v; prefs.edit().putBoolean("font_web", v).apply() }

    /** 사이트에서 찾아둔 게시판 목록 (다음에 바로 보이게 저장) */
    fun savedBoards(site: Site): List<Board> = runCatching {
        val arr = JSONArray(prefs.getString("boards_${site.name}", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Board(o.getString("n"), o.getString("u"), o.optString("s"))
        }
    }.getOrDefault(emptyList())

    fun saveBoards(site: Site, list: List<Board>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("u", it.url).put("s", it.section)) }
        prefs.edit().putString("boards_${site.name}", arr.toString()).apply()
    }

    fun boardName(site: Site): String =
        boardNames[site] ?: (Boards.preset(site) + savedBoards(site)).firstOrNull { it.url == listUrl(site) }?.name ?: "직접 입력한 주소"

    fun setBoard(site: Site, name: String, url: String) {
        setListUrl(site, url)
        boardNames = boardNames + (site to name)
        prefs.edit().putString("board_${site.name}", name).apply()
    }

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("scrap", Context.MODE_PRIVATE)
        readKeys = prefs.getStringSet("read", emptySet())!!.toSet()
        enabled = Site.entries.filter { prefs.getBoolean("on_${it.name}", true) }.toSet()
        listUrls = Site.entries.associateWith { prefs.getString("list_${it.name}", null) ?: it.defaultList }
        boardNames = Site.entries.mapNotNull { s -> prefs.getString("board_${s.name}", null)?.let { s to it } }.toMap()
        font = prefs.getString("font", "system") ?: "system"
        adBlock = prefs.getBoolean("adblock", true)
        unlock = prefs.getBoolean("unlock", true)
        fontInWeb = prefs.getBoolean("font_web", true)
        isolate = prefs.getBoolean("isolate", true)
        bgKey = prefs.getString("bg", "white") ?: "white"
        allTabOn = prefs.getBoolean("all_tab", true)
        startTab = prefs.getString("start_tab", "ALL") ?: "ALL"
        saveTree = prefs.getString("save_tree", null)
        prefs.getString("tab_order", null)?.split(',')?.let { saved ->
            val all = listOf("ALL") + Site.entries.map { it.name }
            tabOrder = saved.filter { it in all } + all.filter { it !in saved }
        }
        bookmarks = runCatching {
            val arr = JSONArray(prefs.getString("bookmarks", "[]"))
            (0 until arr.length()).mapNotNull { i -> fromJson(arr.getJSONObject(i)) }
        }.getOrDefault(emptyList())
    }

    fun markRead(p: Post) {
        if (p.key in readKeys) return
        // 너무 많이 쌓이지 않게 최근 3000개만 기억
        val next = (readKeys + p.key).let { if (it.size > 3000) it.drop(it.size - 3000).toSet() else it }
        readKeys = next
        prefs.edit().putStringSet("read", next).apply()
    }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun toggleBookmark(p: Post): Boolean {
        val on = !isBookmarked(p.url)
        bookmarks = if (on) listOf(p) + bookmarks else bookmarks.filterNot { it.url == p.url }
        saveBookmarks()
        return on
    }

    private fun saveBookmarks() {
        val arr = JSONArray()
        bookmarks.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("bookmarks", arr.toString()).apply()
    }

    fun setEnabled(site: Site, on: Boolean) {
        enabled = if (on) enabled + site else enabled - site
        prefs.edit().putBoolean("on_${site.name}", on).apply()
    }

    fun listUrl(site: Site) = listUrls[site] ?: site.defaultList

    fun setListUrl(site: Site, url: String) {
        val v = url.trim().ifEmpty { site.defaultList }
        listUrls = listUrls + (site to v)
        boardNames = boardNames - site
        prefs.edit().putString("list_${site.name}", v).remove("board_${site.name}").apply()
    }

    /** 해당 사이트에 저장된 쿠키가 있는지(로그인해 둔 상태인지) */
    fun hasLogin(site: Site): Boolean {
        val cm = CookieManager.getInstance()
        return loginMarked(site) && site.cookieUrls.any { u -> !cm.getCookie(u).isNullOrBlank() }
    }

    private fun loginMarked(site: Site) = prefs.getBoolean("login_${site.name}", false)

    fun markLogin(site: Site, on: Boolean) {
        prefs.edit().putBoolean("login_${site.name}", on).apply()
        loginVersion++
    }

    fun logout(site: Site) {
        val cm = CookieManager.getInstance()
        site.cookieUrls.forEach { u ->
            val host = android.net.Uri.parse(u).host.orEmpty()
            val root = host.split('.').takeLast(2).joinToString(".")
            cm.getCookie(u).orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }
                .forEach { name ->
                    cm.setCookie(u, "$name=; Max-Age=0; Path=/")
                    cm.setCookie(u, "$name=; Max-Age=0; Path=/; Domain=.$root")
                    cm.setCookie(u, "$name=; Max-Age=0; Path=/; Domain=$host")
                }
        }
        cm.flush()
        markLogin(site, false)
    }

    private fun toJson(p: Post) = JSONObject().apply {
        put("site", p.site.name); put("id", p.id); put("title", p.title); put("url", p.url)
        p.thumb?.let { put("thumb", it) }
        p.comments?.let { put("comments", it) }
    }

    private fun fromJson(o: JSONObject): Post? = runCatching {
        Post(
            site = Site.valueOf(o.getString("site")),
            id = o.getString("id"),
            title = o.getString("title"),
            url = o.getString("url"),
            thumb = o.optString("thumb").ifEmpty { null },
            comments = if (o.has("comments")) o.getInt("comments") else null,
        )
    }.getOrNull()
}
