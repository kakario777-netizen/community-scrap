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
    /** 탭이 많을 때 쓰는 짧은 이름 */
    val short: String = label,
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
    ),
    DCINSIDE(
        label = "디시인사이드",
        short = "디시",
        color = 0xFF3B4890,
        defaultList = "https://gall.dcinside.com/board/lists/?id=dcbest",
        loginUrl = "https://sign.dcinside.com/login",
        home = "https://gall.dcinside.com/",
        pageParam = "page",
        cookieUrls = listOf("https://gall.dcinside.com", "https://m.dcinside.com", "https://sign.dcinside.com", "https://dcinside.com"),
        postPattern = Regex("""^https?://(?:gall\.dcinside\.com/(?:mgallery/|mini/)?board/view/?\?(?:.*&)?no=|m\.dcinside\.com/(?:board|mini)/[\w-]+/)(\d+)"""),
    ),
    FMKOREA(
        label = "에펨코리아",
        short = "에펨",
        color = 0xFF5B6EE1,
        defaultList = "https://www.fmkorea.com/best",
        loginUrl = "https://www.fmkorea.com/index.php?act=dispMemberLoginForm",
        home = "https://www.fmkorea.com/",
        pageParam = "page",
        cookieUrls = listOf("https://www.fmkorea.com", "https://m.fmkorea.com", "https://fmkorea.com"),
        postPattern = Regex("""^https?://(?:www\.|m\.)?fmkorea\.com/(?:[a-z_0-9]+/)?(?:index\.php\?(?:.*&)?document_srl=)?(\d{6,})(?:[/?#&].*)?$"""),
    ),
    BOBAEDREAM(
        label = "보배드림",
        short = "보배",
        color = 0xFF00897B,
        defaultList = "https://www.bobaedream.co.kr/list?code=best",
        loginUrl = "https://www.bobaedream.co.kr/member/login",
        home = "https://www.bobaedream.co.kr/",
        pageParam = "page",
        cookieUrls = listOf("https://www.bobaedream.co.kr", "https://m.bobaedream.co.kr", "https://bobaedream.co.kr"),
        postPattern = Regex("""^https?://(?:(?:www\.)?bobaedream\.co\.kr/view\?(?:.*&)?[Nn]o=|m\.bobaedream\.co\.kr/board/bbs_view/[\w-]+/)(\d+)"""),
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
    /** 작성자 */
    val info: String? = null,
    /** 말머리·주제 */
    val category: String? = null,
    val time: String? = null,
    val views: Int? = null,
    val likes: Int? = null,
) {
    val key get() = "${site.name}:$id"
}

/** 게시판 안의 소분류(카테고리·말머리) */
data class Category(val name: String, val url: String)
