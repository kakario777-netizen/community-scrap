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
    )

    private val commentSelectors = listOf(
        ".comment-count", ".num_reply", ".reply_num", ".num_comment", ".cmt", ".comment", ".reply", ".cnt_cmt",
    )

    private val infoSelectors = mapOf(
        Site.ARCA to listOf(".vcol.col-author", ".user-info"),
        Site.RULIWEB to listOf(".writer", ".nick"),
        Site.GOODGAG to listOf(".nick", ".writer", ".name"),
    )

    fun parse(site: Site, html: String, baseUrl: String): List<Post> {
        val doc = Jsoup.parse(html, baseUrl)
        val found = LinkedHashMap<String, Post>()

        for (a in doc.select("a[href]")) {
            val href = a.absUrl("href")
            if (href.isEmpty()) continue
            val m = site.postPattern.find(href) ?: continue
            val id = m.groupValues.last()
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

            val prev = found[id]
            if (prev == null) {
                found[id] = Post(site, id, title, cleanUrl(href), thumb, comments, info)
            } else {
                // 같은 글을 가리키는 링크가 여러 개(썸네일 링크, 제목 링크)면 합친다
                found[id] = prev.copy(
                    title = if (title.length > prev.title.length) title else prev.title,
                    thumb = prev.thumb ?: thumb,
                    comments = prev.comments ?: comments,
                    info = prev.info ?: info,
                )
            }
        }
        return found.values.filter { it.title.length >= 2 }
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
