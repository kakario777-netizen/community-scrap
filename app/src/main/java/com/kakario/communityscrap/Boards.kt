package com.kakario.communityscrap

import android.content.Context
import org.jsoup.Jsoup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class Board(val name: String, val url: String, val section: String = "")

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
        Site.RULIWEB -> listOf(
            "https://bbs.ruliweb.com/best", "https://bbs.ruliweb.com/",
            "https://bbs.ruliweb.com/community", "https://bbs.ruliweb.com/hobby",
            "https://bbs.ruliweb.com/game", "https://bbs.ruliweb.com/market", "https://bbs.ruliweb.com/news",
        )
    }

    private val boardPattern = mapOf(
        Site.GOODGAG to Regex("""^https?://(?:www\.)?goodgag\.net/([a-z][a-z_]*)/?$"""),
        Site.ARCA to Regex("""^https?://arca\.live/b/([\w-]+)/?$"""),
        Site.RULIWEB to Regex("""^https?://(?:bbs|m)\.ruliweb\.com/(?:[\w-]+/)*(?:board/\d+|best/[\w-]+)(?:/list)?/?$"""),
    )

    private val skipWords = setOf("login", "my", "story", "write", "member", "search", "join", "logout", "notice", "policy")

    fun extract(site: Site, html: String, base: String): List<Board> {
        val doc = Jsoup.parse(html, base)
        val pat = boardPattern.getValue(site)
        val out = LinkedHashMap<String, Board>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href").substringBefore('#').substringBefore('?').trimEnd('/').removeSuffix("/list")
                .replace("://m.ruliweb.com/", "://bbs.ruliweb.com/")
            val m = pat.find(href) ?: continue
            if (site == Site.GOODGAG && m.groupValues[1] in skipWords) continue
            val name = (a.attr("title").ifBlank { a.text() }).trim().replace(Regex("\\s+"), " ")
            if (name.length < 2 || name.length > 30) continue
            if (href !in out) out[href] = Board(name, href, sectionOf(site, href))
        }
        return out.values.toList()
    }

    private val ruliSections = mapOf(
        "best" to "베스트", "community" to "커뮤니티", "hobby" to "취미", "market" to "장터", "news" to "뉴스",
        "game" to "게임", "ps" to "플레이스테이션", "nin" to "닌텐도", "xbox" to "XBOX", "pc" to "PC 게임",
        "mobile" to "모바일 게임", "family" to "패밀리", "etcs" to "기타", "av" to "AV·영상기기",
    )

    /** 게시판이 속한 구역 이름 */
    fun sectionOf(site: Site, url: String): String = when (site) {
        Site.RULIWEB -> {
            val first = android.net.Uri.parse(url).pathSegments.firstOrNull().orEmpty()
            ruliSections[first] ?: first.ifEmpty { "기타" }
        }
        Site.ARCA -> "채널"
        Site.GOODGAG -> "게시판"
    }

    /** 사이트 메뉴를 읽어 게시판을 찾아온다 (로그인 쿠키 그대로 사용, 여러 페이지 동시에) */
    suspend fun discover(ctx: Context, site: Site): List<Board> = coroutineScope {
        val results = sources(site).map { src ->
            async {
                val html = Net.getHtml(src)
                if (html != null) runCatching { extract(site, html, src) }.getOrNull().orEmpty()
                else WebFetcher.load(ctx, src, timeoutMs = 15_000) { h ->
                    runCatching { extract(site, h, src) }.getOrNull()?.takeIf { it.isNotEmpty() }
                }.orEmpty()
            }
        }.awaitAll()
        val all = LinkedHashMap<String, Board>()
        results.flatten().forEach { b -> if (b.url !in all) all[b.url] = b }
        all.values.toList()
    }
}
