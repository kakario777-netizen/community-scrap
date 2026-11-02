package com.kakario.communityscrap

/** 모아볼 사이트 목록. 목록 주소는 설정에서 바꿀 수 있다. */
enum class Site(
    val label: String,
    val color: Long,
    val defaultList: String,
    val loginUrl: String,
    val home: String,
    /** 목록 페이지 번호에 쓰는 주소 파라미터 이름 */
    val pageParam: String,
    /** 로그아웃할 때 쿠키를 지울 주소들 */
    val cookieUrls: List<String>,
    /** 글 주소 패턴. 마지막 그룹이 글 번호 */
    val postPattern: Regex,
) {
    GOODGAG(
        label = "고급유머",
        color = 0xFFE8590C,
        defaultList = "https://www.goodgag.net/",
        loginUrl = "https://www.goodgag.net/login/main",
        home = "https://www.goodgag.net/",
        pageParam = "page",
        cookieUrls = listOf("https://www.goodgag.net", "https://goodgag.net"),
        postPattern = Regex("""^https?://(?:www\.|m\.)?goodgag\.net/(\d{3,})/?(?:[?#].*)?$"""),
    ),
    ARCA(
        label = "아카라이브",
        color = 0xFF2F6FDB,
        defaultList = "https://arca.live/b/live",
        loginUrl = "https://arca.live/u/login",
        home = "https://arca.live/",
        pageParam = "p",
        cookieUrls = listOf("https://arca.live"),
        postPattern = Regex("""^https?://arca\.live/b/[\w-]+/(\d+)(?:[/?#].*)?$"""),
    ),
    RULIWEB(
        label = "루리웹",
        color = 0xFF2E7D32,
        defaultList = "https://bbs.ruliweb.com/best/humor_only",
        loginUrl = "https://user.ruliweb.com/member/login",
        home = "https://bbs.ruliweb.com/",
        pageParam = "page",
        cookieUrls = listOf("https://bbs.ruliweb.com", "https://m.ruliweb.com", "https://user.ruliweb.com", "https://ruliweb.com"),
        postPattern = Regex("""^https?://(?:bbs|m)\.ruliweb\.com/.*/read/(\d+)(?:[/?#].*)?$"""),
    );

    fun pageUrl(base: String, page: Int): String {
        if (page <= 1) return base
        val clean = base.replace(Regex("""([?&])${pageParam}=\d+&?"""), "$1").trimEnd('?', '&')
        val sep = if (clean.contains('?')) '&' else '?'
        return "$clean$sep$pageParam=$page"
    }

    companion object {
        fun forUrl(url: String): Site? = entries.firstOrNull { s ->
            val host = android.net.Uri.parse(url).host ?: return@firstOrNull false
            s.cookieUrls.any { android.net.Uri.parse(it).host?.let { h -> host.endsWith(h.removePrefix("www.")) } == true }
        }
    }
}

data class Post(
    val site: Site,
    val id: String,
    val title: String,
    val url: String,
    val thumb: String? = null,
    val comments: Int? = null,
    val info: String? = null,
) {
    val key get() = "${site.name}:$id"
}
