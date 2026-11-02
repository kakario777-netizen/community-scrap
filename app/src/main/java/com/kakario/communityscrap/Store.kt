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

    fun updateFont(v: String) { font = v; prefs.edit().putString("font", v).apply() }
    fun updateAdBlock(v: Boolean) { adBlock = v; prefs.edit().putBoolean("adblock", v).apply() }
    fun updateUnlock(v: Boolean) { unlock = v; prefs.edit().putBoolean("unlock", v).apply() }
    fun updateFontInWeb(v: Boolean) { fontInWeb = v; prefs.edit().putBoolean("font_web", v).apply() }

    fun boardName(site: Site): String =
        boardNames[site] ?: Boards.preset(site).firstOrNull { it.url == listUrl(site) }?.name ?: "직접 입력한 주소"

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
