package com.kakario.communityscrap

import android.content.Context
import org.jsoup.Jsoup

data class Board(val name: String, val url: String)

/** 게시판 고르기: 자주 쓰는 게시판 + 사이트 메뉴에서 찾아낸 게시판 */
object Boards {

    fun preset(site: Site): List<Board> = when (site) {
        Site.GOODGAG -> listOf(
            Board("고급유머 메인", "https://www.goodgag.net/"),
            Board("잇슈", "https://www.goodgag.net/issue"),
            Board("익게", "https://www.goodgag.net/ik"),
        )
        Site.ARCA -> listOf(
            Board("실시간 베스트", "https://arca.live/b/live"),
        )
        Site.RULIWEB -> listOf(
            Board("유머 베스트", "https://bbs.ruliweb.com/best/humor_only"),
            Board("유머 게시판", "https://bbs.ruliweb.com/community/board/300143"),
        )
    }

    /** 게시판 목록을 찾으러 열어볼 페이지 */
    private fun sources(site: Site): List<String> = when (site) {
        Site.GOODGAG -> listOf("https://www.goodgag.net/")
        Site.ARCA -> listOf("https://arca.live/channels", "https://arca.live/")
        Site.RULIWEB -> listOf("https://bbs.ruliweb.com/best", "https://bbs.ruliweb.com/")
    }

    private val boardPattern = mapOf(
        Site.GOODGAG to Regex("""^https?://(?:www\.)?goodgag\.net/([a-z][a-z_]*)/?$"""),
        Site.ARCA to Regex("""^https?://arca\.live/b/([\w-]+)/?$"""),
        Site.RULIWEB to Regex("""^https?://(?:bbs|m)\.ruliweb\.com/(?:[\w-]+/)*(?:board/\d+|best/[\w-]+)/?$"""),
    )

    private val skipWords = setOf("login", "my", "story", "write", "member", "search", "join", "logout", "notice", "policy")

    fun extract(site: Site, html: String, base: String): List<Board> {
        val doc = Jsoup.parse(html, base)
        val pat = boardPattern.getValue(site)
        val out = LinkedHashMap<String, Board>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href").substringBefore('#').substringBefore('?').trimEnd('/')
            val m = pat.find(href) ?: continue
            if (site == Site.GOODGAG && m.groupValues[1] in skipWords) continue
            val name = (a.attr("title").ifBlank { a.text() }).trim().replace(Regex("\\s+"), " ")
            if (name.length < 2 || name.length > 30) continue
            if (href !in out) out[href] = Board(name, href)
        }
        return out.values.toList()
    }

    /** 사이트 메뉴를 읽어 게시판을 찾아온다 (로그인 쿠키 그대로 사용) */
    suspend fun discover(ctx: Context, site: Site): List<Board> {
        val all = LinkedHashMap<String, Board>()
        for (src in sources(site)) {
            val html = Net.getHtml(src)
            var found = html?.let { runCatching { extract(site, it, src) }.getOrNull() }.orEmpty()
            if (found.isEmpty()) {
                found = WebFetcher.load(ctx, src) { h -> runCatching { extract(site, h, src) }.getOrNull() }.orEmpty()
            }
            found.forEach { b -> if (b.url !in all) all[b.url] = b }
        }
        return all.values.toList()
    }
}
