package com.kakario.communityscrap

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 사이트 페이지 HTML에서 글 목록을 뽑아낸다.
 * 사이트 디자인이 조금 바뀌어도 버티도록, 글 주소 패턴에 맞는 링크를 모두 찾은 뒤
 * 그 링크가 속한 줄(행)에서 제목·썸네일·댓글 수를 고른다.
 */
object Parser {

    private val titleSelectors = mapOf(
        Site.ARCA to listOf(".title", ".vrow-top .title"),
        Site.RULIWEB to listOf(".subject_link", "a.deco", ".subject .text_over", ".title_wrapper"),
        Site.GOODGAG to listOf(".title", ".subject", ".tit", "h2", "h3", "strong"),
        Site.DCINSIDE to listOf(".gall_tit a:not(.reply_numbox)", ".subjectin", ".subject-add .subjectin", ".detail-txt"),
        Site.FMKOREA to listOf("h3.title", ".title a", ".title", ".hotdeal_var8"),
        Site.BOBAEDREAM to listOf("a.bsubject", ".bsubject", ".txt", ".title"),
    )

    private val commentSelectors = listOf(
        ".comment-count", ".num_reply", ".reply_num", ".num_comment", ".comment_count", ".totreply", ".ct",
        ".cmt", ".comment", ".reply", ".cnt_cmt",
    )

    private val infoSelectors = mapOf(
        Site.ARCA to listOf(".vcol.col-author", ".user-info"),
        Site.RULIWEB to listOf(".writer", ".nick"),
        Site.GOODGAG to listOf(".nick", ".writer", ".name"),
        Site.DCINSIDE to listOf(".gall_writer .nickname", ".gall_writer", ".ginfo li:first-child"),
        Site.FMKOREA to listOf(".author", ".member"),
        Site.BOBAEDREAM to listOf(".author02", ".author"),
    )

    private val categorySelectors = mapOf(
        Site.ARCA to listOf(".badge", ".vrow-top .badge"),
        Site.RULIWEB to listOf(".divsn", ".category", ".cate"),
        Site.GOODGAG to listOf(".category", ".cate", ".tag", "[class*=category]", "[class*=cate]"),
        Site.DCINSIDE to listOf(".gall_subject", ".subject-add .sp-lst-txt"),
        Site.FMKOREA to listOf(".category a", ".category"),
        Site.BOBAEDREAM to listOf(".category", ".cate"),
    )
    private val timeSelectors = listOf("time", ".col-time", ".gall_date", ".time", ".date", ".regdate", ".datetime", "[class*=date]")
    private val viewSelectors = listOf(".col-view", ".gall_count", ".hit", ".view_count", ".views", ".count_view", ".read", ".count", "[class*=hit]", "[class*=view_cnt]")
    private val likeSelectors = listOf(".col-rate", ".gall_recommend", ".recomd", ".recomm", ".recommend", ".pc_voted_count", ".voted_count", ".like", ".likes", ".vote", ".up", "[class*=recom]", "[class*=like]")

    private fun textOf(row: Element, sels: List<String>, maxLen: Int): String? {
        for (sel in sels) {
            val el = row.selectFirst(sel) ?: continue
            val t = el.text().trim().replace(Regex("\\s+"), " ")
            if (t.isNotEmpty() && t.length <= maxLen) return t
        }
        return null
    }

    private fun numberOf(row: Element, sels: List<String>): Int? {
        for (sel in sels) {
            val t = row.selectFirst(sel)?.text()?.trim() ?: continue
            if (t.length > 14) continue
            val clean = t.replace(",", "")
            Regex("""(\d+(?:\.\d+)?)\s*만""").find(clean)?.let { return (it.groupValues[1].toDouble() * 10000).toInt() }
            Regex("""\d{1,9}""").find(clean)?.value?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun pickTime(row: Element): String? {
        val t = row.selectFirst("time")
        if (t != null) {
            val txt = t.text().trim()
            if (txt.isNotEmpty() && txt.length <= 20) return txt
            val dt = t.attr("datetime")
            if (dt.length >= 16) return dt.substring(5, 16).replace('T', ' ').replace('-', '.')
        }
        return textOf(row, timeSelectors.drop(1), 20)
    }

    private fun pickCategory(site: Site, a: Element, row: Element): String? {
        for (sel in categorySelectors[site].orEmpty()) {
            val el = a.selectFirst(sel) ?: row.selectFirst(sel) ?: continue
            val t = el.text().trim().trim('[', ']', '(', ')').trim()
            if (t.isNotEmpty() && t.length <= 12 && !t.all { it.isDigit() }) return t
        }
        return null
    }

    private val boardIds = listOf("id", "code", "mid")
    private fun param(q: String?, k: String): String? =
        q?.split('&')?.firstOrNull { it.substringBefore('=').equals(k, true) }?.substringAfter('=', "")

    /** 게시판 페이지 안의 소분류 링크(같은 게시판 주소 + 분류 파라미터) */
    fun categories(site: Site, html: String, baseUrl: String): List<Category> {
        val base = runCatching { java.net.URI(baseUrl) }.getOrNull() ?: return emptyList()
        val basePath = (base.path ?: "").trimEnd('/')
        val keys = setOf("category", "cate", "ctg", "sca", "bo_cat", "cat", "division", "tag", "mode", "search_head", "exception_mode", "s_cate")
        val doc = Jsoup.parse(html, baseUrl)
        val out = LinkedHashMap<String, Category>()
        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href").substringBefore('#')
            if (href.isEmpty() || site.postPattern.containsMatchIn(href)) continue
            val u = runCatching { java.net.URI(href) }.getOrNull() ?: continue
            if (u.host != base.host) continue
            val path = (u.path ?: "").trimEnd('/')
            if (!(path == basePath || path == "$basePath/list")) continue
            val query = u.rawQuery ?: continue
            // 같은 주소라도 게시판 번호(id·code·mid)가 다르면 다른 게시판
            if (boardIds.any { k -> param(base.rawQuery, k) != null && param(base.rawQuery, k) != param(query, k) }) continue
            val hit = query.split('&').any { kv ->
                val k = kv.substringBefore('=').lowercase()
                k in keys && kv.substringAfter('=', "").isNotEmpty()
            }
            if (!hit) continue
            val name = a.text().trim().replace(Regex("\\s+"), " ")
            if (name.isEmpty() || name.length > 15 || name.all { it.isDigit() }) continue
            if (name !in out) out[name] = Category(name, href)
        }
        return out.values.toList()
    }

    /** 목록 옆 칸·광고·쇼핑 묶음 안의 링크는 게시판 글이 아니다 */
    private val sideWords = Regex("""(^|[\s_-])(aside|sidebar|ad|ads|banner|shopping|shop|live-?shop|sponsor|promotion|footer)([\s_-]|$)""", RegexOption.IGNORE_CASE)

    private fun inSideArea(a: Element): Boolean {
        var n: Element? = a
        var d = 0
        while (n != null && d < 10) {
            if (n.tagName() == "aside" || n.tagName() == "footer") return true
            if (sideWords.containsMatchIn(n.className() + " " + n.id())) return true
            n = n.parent(); d++
        }
        return false
    }

    /** 제목이 날짜·시간·숫자뿐이면 글이 아니다 */
    private val notTitle = Regex("""^[\d\s.:/\-()년월일시분초전]+$""")

    fun parse(site: Site, html: String, baseUrl: String): List<Post> {
        val strict = parse(site, html, baseUrl, strict = true)
        return strict.ifEmpty { parse(site, html, baseUrl, strict = false) }
    }

    private fun parse(site: Site, html: String, baseUrl: String, strict: Boolean): List<Post> {
        val doc = Jsoup.parse(html, baseUrl)
        val found = LinkedHashMap<String, Post>()
        // 아카라이브: 채널 게시판이면 그 채널 글만 (다른 채널 홍보·쇼핑 글 제외)
        val arcaChannel = if (site == Site.ARCA) Regex("""arca\.live/b/([\w-]+)""").find(baseUrl)?.groupValues?.get(1) else null

        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href")
            if (href.isEmpty()) continue
            val m = site.postPattern.find(href) ?: continue
            val id = m.groupValues.last()
            if (strict && arcaChannel != null && arcaChannel != "live" &&
                Regex("""arca\.live/b/([\w-]+)/""").find(href)?.groupValues?.get(1) != arcaChannel
            ) continue
            if (strict && inSideArea(a)) continue
            val row = rowOf(a, site)
            if (isNotice(a) || isNotice(row)) continue

            val rawTitle = pickTitle(site, a, row)
            var (title, cnt) = splitCount(rawTitle)
            val comments = cnt ?: pickComments(row)
            val thumb = pickThumb(row) ?: pickThumb(a)
            val info = infoSelectors[site]?.firstNotNullOfOrNull { sel ->
                row.selectFirst(sel)?.text()?.trim()?.takeIf { it.isNotEmpty() && it.length < 30 }
            }
            title = title.trim()
            val category = pickCategory(site, a, row)
            if (category != null && title.startsWith(category)) title = title.removePrefix(category).trim()
            val time = pickTime(row)
            val views = numberOf(row, viewSelectors)
            val likes = numberOf(row, likeSelectors)

            val prev = found[id]
            if (prev == null) {
                found[id] = Post(site, id, title, cleanUrl(href), thumb, comments, info, category, time, views, likes)
            } else {
                // 같은 글을 가리키는 링크가 여러 개(썸네일 링크, 제목 링크)면 합친다
                found[id] = prev.copy(
                    title = if (title.length > prev.title.length) title else prev.title,
                    thumb = prev.thumb ?: thumb,
                    comments = prev.comments ?: comments,
                    info = prev.info ?: info,
                    category = prev.category ?: category,
                    time = prev.time ?: time,
                    views = prev.views ?: views,
                    likes = prev.likes ?: likes,
                )
            }
        }
        return found.values.filter { it.title.length >= 2 && !notTitle.matches(it.title) }
    }

    private fun cleanUrl(href: String): String = href.substringBefore('#')

    /** 링크가 속한 한 줄. 줄 안에 다른 글 링크가 여럿 있으면 너무 넓게 잡은 것이므로 링크 자체를 쓴다. */
    private fun rowOf(a: Element, site: Site): Element {
        if (a.hasClass("vrow")) return a
        var cur: Element? = a.parent()
        var depth = 0
        while (cur != null && depth < 6) {
            val tag = cur.tagName()
            if (tag == "tr" || tag == "li" || tag == "article" || cur.hasClass("vrow") ||
                cur.className().contains("item") || cur.className().contains("row")
            ) {
                val ids = cur.select("a[href]").mapNotNull { site.postPattern.find(it.absUrl("href"))?.groupValues?.last() }.toSet()
                return if (ids.size <= 1) cur else a
            }
            if (tag == "body" || tag == "ul" || tag == "tbody" || tag == "table") break
            cur = cur.parent()
            depth++
        }
        return a
    }

    private fun isNotice(e: Element): Boolean {
        val c = e.className().lowercase()
        return c.contains("notice") || c.contains("ad_") || c.contains("-ad")
    }

    private fun pickTitle(site: Site, a: Element, row: Element): String {
        for (sel in titleSelectors[site].orEmpty()) {
            val t = (a.selectFirst(sel) ?: row.selectFirst(sel))?.let { el ->
                // 제목 안에 붙은 댓글수·말머리 아이콘 등을 빼고 본문 텍스트만
                el.clone().apply { select(".comment-count,.num_reply,.badge,.category,.vrow-preview,img").remove() }.text()
            }?.trim()
            if (!t.isNullOrEmpty() && t.length >= 2) return t
        }
        val own = a.text().trim()
        if (own.length >= 2) return own
        return a.attr("title").trim().ifEmpty { a.selectFirst("img[alt]")?.attr("alt")?.trim().orEmpty() }
    }

    private val countTail = Regex("""\s*[\[(]\s*(\d{1,5})\s*[\])]\s*$""")

    private fun splitCount(t: String): Pair<String, Int?> {
        val m = countTail.find(t) ?: return t to null
        return t.removeRange(m.range) to m.groupValues[1].toIntOrNull()
    }

    private fun pickComments(row: Element): Int? {
        for (sel in commentSelectors) {
            val txt = row.selectFirst(sel)?.text() ?: continue
            val n = Regex("""\d{1,5}""").find(txt)?.value?.toIntOrNull()
            if (n != null) return n
        }
        return null
    }

    private fun pickThumb(e: Element): String? {
        for (img in e.select("img")) {
            val src = listOf("data-src", "data-original", "data-lazy-src", "src")
                .map { img.absUrl(it).ifEmpty { img.attr(it) } }
                .firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
            if (src == null) continue
            val full = if (src.startsWith("//")) "https:$src" else src
            val low = full.lowercase()
            if (low.contains("icon") || low.contains("emoticon") || low.contains("/level") ||
                low.contains("blank") || low.contains("noimg") || low.endsWith(".svg")
            ) continue
            val w = img.attr("width").toIntOrNull()
            if (w != null && w < 30) continue
            return full
        }
        // 배경 이미지로 썸네일을 넣는 경우
        e.select("[style*=background]").forEach { el ->
            Regex("""url\(['"]?([^'")]+)""").find(el.attr("style"))?.groupValues?.get(1)?.let { u ->
                return if (u.startsWith("//")) "https:$u" else org.jsoup.internal.StringUtil.resolve(el.baseUri(), u)
            }
        }
        return null
    }
}
