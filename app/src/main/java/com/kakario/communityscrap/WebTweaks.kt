package com.kakario.communityscrap

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream

/** 글 화면(웹뷰)에 덧붙이는 기능: 광고 차단, 다운로드 막기 풀기, 글꼴 */
object WebTweaks {

    /** 광고·추적 주소 (이 주소들로 가는 요청은 빈 응답으로 막는다) */
    private val adHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.",
        "pagead2.", "googletagservices.com", "imasdk.googleapis.com", "fundingchoicesmessages.google.com",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "adnxs.com", "amazon-adsystem.com",
        "mobon.net", "dable.io", "realclick.co.kr", "adfit.kakao.com", "display.ad.daum.net", "kas.daumcdn.net",
        "adpopcorn.com", "tenping.kr", "adop.cc", "cauly.co.kr", "adlibr.com", "mman.kr", "ad.about.co.kr",
        "aniview.com", "teads.tv", "smartadserver.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "adsrvr.org", "moatads.com", "scorecardresearch.com", "popads.net", "propellerads.com",
        "exelator.com", "bidswitch.net", "adform.net", "yieldmo.com", "sharethrough.com", "33across.com",
        "adtive.com", "interworksmedia.co.kr", "targetpush.co.kr", "mediacategory.com", "acrosspf.com",
    )

    /** 광고로 자주 쓰이는 자리 숨기기 */
    private const val AD_CSS = """
ins.adsbygoogle, [id^="google_ads"], [id*="div-gpt-ad"], iframe[src*="doubleclick"], iframe[src*="googlesyndication"],
iframe[id^="google_ads"], [class~="ad"], [class~="ads"], [class~="adsense"], [class^="ad_"], [class*=" ad_"],
[class^="ad-"], [class*=" ad-"], [class$="-ad"], [class*="-ad "], [class*="_ad "], [class$="_ad"],
[class*="advert"], [class*="banner_ad"], [class*="ad_banner"], [class*="adBanner"], [class*="adsbox"],
[id^="ad_"], [id^="ad-"], [id$="_ad"], [id$="-ad"], [id*="adsense"], [id*="advert"], [id*="banner_ad"],
[data-ad], [data-ad-slot], [data-adunit], .revenue_unit_wrap, .ad_wrapper, .board_ad, .ad_area, .ad_box,
.topbar-area .ad, .sticky-container .ad, a[href*="ad.doubleclick"], a[href*="adclick"]
{ display:none !important; height:0 !important; min-height:0 !important; }
"""

    /** 길게 눌러 저장·복사를 막아둔 것 풀기 */
    private const val UNLOCK_CSS = """
*, *::before, *::after { -webkit-user-select:auto !important; user-select:auto !important; -webkit-touch-callout:default !important; }
img, video { pointer-events:auto !important; -webkit-user-drag:auto !important; }
"""

    private const val UNLOCK_JS = """
(function(){
  if (window.__csUnlock) return; window.__csUnlock = true;
  ['contextmenu','selectstart','dragstart','copy','cut','mousedown','touchstart'].forEach(function(t){
    window.addEventListener(t, function(e){ if (t==='mousedown'||t==='touchstart') return; e.stopImmediatePropagation(); }, true);
  });
  var clear = function(){
    try {
      document.oncontextmenu = null; document.onselectstart = null; document.ondragstart = null; document.oncopy = null;
      if (document.body) { document.body.oncontextmenu = null; document.body.onselectstart = null; document.body.ondragstart = null; }
      document.querySelectorAll('[oncontextmenu],[onselectstart],[ondragstart]').forEach(function(e){
        e.removeAttribute('oncontextmenu'); e.removeAttribute('onselectstart'); e.removeAttribute('ondragstart');
      });
    } catch(e) {}
  };
  clear(); setTimeout(clear, 800); setTimeout(clear, 2500);
})();
"""

    /** 사이트별 본문·댓글 자리 (앞에 있는 것부터 찾아본다) */
    private val articleSel = mapOf(
        Site.ARCA to listOf(".article-wrapper", ".article-body", ".article-content"),
        Site.RULIWEB to listOf(".board_main_view", "#board_read", ".board_main", ".view_content"),
        Site.GOODGAG to listOf("article", ".view_wrap", ".view", ".post_view", ".board_view", "[class*=view_]"),
        Site.DCINSIDE to listOf(".view_content_wrap", ".gallview_contents", ".writing_view_box", ".gall-thum-btm", ".thum-txtin"),
        Site.FMKOREA to listOf(".rd", "article", ".rd_body", ".xe_content"),
        Site.BOBAEDREAM to listOf(".writerProfile + .bodyCont", ".content02", ".bodyCont", ".article-body", ".view-body"),
    )
    private val commentSel = mapOf(
        Site.ARCA to listOf("#comment", ".article-comment", ".list-area"),
        Site.RULIWEB to listOf(".comment_view_wrapper", "#cmt", ".comment_wrapper", ".comment_view"),
        Site.GOODGAG to listOf("#comment", "#comments", ".comment_wrap", ".comment-area", ".comments", ".comment_list", ".cmt_list", "[class*=comment]", "[id*=cmt]"),
        Site.DCINSIDE to listOf(".comment_wrap", "#focus_cmt", ".view_comment", ".all-comment", ".comment-box"),
        Site.FMKOREA to listOf("#cmtPosition", ".fdb_lst", "#comment", ".comment_wrap"),
        Site.BOBAEDREAM to listOf("#cmt_list", ".commentlistbox", ".cmt-list", ".comment"),
    )
    private val genericArticle = listOf("article", "[class*=article]", "[class*=view_cont]", "[class*=post_cont]", "[class*=read_body]", "main")
    private val genericComment = listOf("[id*=comment]", "[class*=comment_wrap]", "[class*=comment-wrap]", "[class*=cmt]", "[class*=reply]")

    /** 본문·댓글만 남기고 나머지(사이트 머리말·메뉴·옆 칸·아래 글목록)를 숨긴다 */
    private fun isolateJs(site: Site, title: String): String {
        fun arr(l: List<String>) = l.joinToString(",", "[", "]") { "'" + it.replace("'", "\\'") + "'" }
        val own = articleSel[site].orEmpty().size
        val t = title.take(14).replace("\\", "").replace("'", "").replace("\n", " ")
        return """
(function(A,OWN,C,T,P){
  try {
    var BAD = /(^|[\s_-])(comment|cmt|reply|write|form|login|footer|header|gnb|menu|sidebar|aside)/i;
    function bad(e){
      for (var n=e, d=0; n && n!==document.body && d<6; n=n.parentElement, d++){
        if (BAD.test((n.id||'')+' '+(typeof n.className==='string'?n.className:''))) return true;
        if (n.tagName==='FORM' || n.tagName==='HEADER' || n.tagName==='NAV' || n.tagName==='FOOTER') return true;
      }
      return false;
    }
    function shown(e){
      if (!e.getClientRects().length) return false;
      var cs = getComputedStyle(e);
      return cs.display!=='none' && cs.visibility!=='hidden';
    }
    function score(e){
      var txt = e.innerText || '';
      var s = txt.length + e.querySelectorAll('img,video,iframe').length*300;
      if (T && txt.indexOf(T) >= 0) s += 3000;
      if (e.querySelector('textarea') && txt.length < 400) s -= 2000;
      return s;
    }
    document.querySelectorAll('[data-cs-hidden]').forEach(function(e){ e.style.removeProperty('display'); e.removeAttribute('data-cs-hidden'); });
    var art=null, bs=-1;
    for (var i=0;i<A.length;i++){
      document.querySelectorAll(A[i]).forEach(function(e){
        if (e===document.body || e===document.documentElement || bad(e) || !shown(e)) return;
        if (e.contains(document.querySelector('header,nav'))) return;
        var s=score(e); if (i<OWN) s+=500;
        if (s>bs){bs=s;art=e;}
      });
      if (i<OWN && art && bs>=600) break;
    }
    // 앞에서 잘 찾은 본문이 남아 있으면 그대로 쓴다 (늦게 붙는 내용 때문에 엉뚱한 곳으로 바뀌지 않게)
    var prev = window.__csArt;
    if (prev && document.contains(prev) && shown(prev) && (!art || score(prev) >= bs*0.7)) { art = prev; bs = score(prev); }
    if (!art || bs < 120) return 'none';
    // 본문 영역이 너무 넓게 잡혔으면, 내용 대부분을 가진 안쪽 칸으로 좁힌다
    for (var dd=0; dd<5; dd++){
      var bk=null, b2=-1;
      Array.prototype.forEach.call(art.children, function(k){ if (!shown(k)) return; var s2=score(k); if (s2>b2){b2=s2;bk=k;} });
      if (bk && b2 >= bs*0.92 && !bad(bk)) { art=bk; bs=b2; } else break;
    }
    window.__csArt = art;
    var keep=[art];
    // 댓글 목록과 댓글 쓰기 칸이 따로 떨어져 있어도 모두 남긴다
    var cms=[];
    for (var j=0;j<C.length;j++){
      document.querySelectorAll(C[j]).forEach(function(e){
        if (art.contains(e) || e.contains(art) || e===document.body) return;
        if (cms.some(function(x){ return x===e || x.contains(e); })) return;
        cms = cms.filter(function(x){ return !e.contains(x); });
        cms.push(e);
      });
    }
    // 너무 큰(페이지 전체를 감싸는) 칸은 빼고, 남은 댓글 칸들의 공통 부모가 가까우면 부모째 남긴다
    cms = cms.filter(function(e){ return !e.contains(document.querySelector('header,nav')); });
    if (cms.length > 1) {
      var par = cms[0].parentElement;
      while (par && !cms.every(function(x){ return par.contains(x); })) par = par.parentElement;
      if (par && par!==document.body && !par.contains(art) && !art.contains(par)) cms = [par];
    }
    var cm = cms[0] || null;
    // 댓글 칸 아래에 붙은 '다른 글 목록'(제목은 안 보이고 [댓글수]만 남던 것)은 숨긴다
    try {
      var PR = new RegExp(P);
      var curM = location.href.match(PR);
      var curId = curM ? curM[curM.length-1] : null;
      var rows = [];
      cms.forEach(function(box){
        box.querySelectorAll('a[href]').forEach(function(a){
          var m = a.href.match(PR);
          if (!m || m[m.length-1] === curId) return;
          var row = a.closest('li,tr,dl') || a;
          if (box.contains(row) && row !== box) rows.push(row);
        });
      });
      rows.forEach(function(r){ r.style.setProperty('display','none','important'); r.setAttribute('data-cs-hidden','1'); });
      // 목록 묶음이 통째로 비면 묶음째 숨긴다
      rows.forEach(function(r){
        var list = r.parentElement && r.parentElement.closest('ul,ol,table,dl');
        var items = list ? list.querySelectorAll('li,tr') : [];
        if (list && !list.querySelector('textarea') && items.length &&
            Array.prototype.every.call(items, function(x){ return !!x.closest('[data-cs-hidden]'); })) {
          list.style.setProperty('display','none','important'); list.setAttribute('data-cs-hidden','1');
        }
      });
    } catch(e) {}
    cms.forEach(function(e){ keep.push(e); });
    function holds(n){ return keep.some(function(k){ return n===k || n.contains(k); }); }
    // 남길 자리와 그 위 경로에만 표시를 달고, 나머지는 CSS 규칙으로 숨긴다.
    // 사이트가 나중에 붙이는 요소도 같은 규칙에 걸려 처음부터 안 보인다.
    document.querySelectorAll('[data-cs-path],[data-cs-root]').forEach(function(e){ e.removeAttribute('data-cs-path'); e.removeAttribute('data-cs-root'); });
    keep.forEach(function(k){
      k.setAttribute('data-cs-root','1');
      for (var n=k.parentElement; n && n!==document.documentElement; n=n.parentElement) n.setAttribute('data-cs-path','1');
    });
    var st = document.getElementById('cs-iso');
    if (!st) { st = document.createElement('style'); st.id = 'cs-iso'; (document.head||document.documentElement).appendChild(st); }
    st.textContent = 'body *:not([data-cs-path]):not([data-cs-root]):not([data-cs-root] *){display:none !important}' +
      '[data-cs-path]{position:static !important;transform:none !important;padding-top:0 !important;margin-top:0 !important;min-height:0 !important}' +
      '[data-cs-root] header,[data-cs-root] nav,[data-cs-root] [class*="gnb"],[data-cs-root] [class*="navbar"],[data-cs-root] [class*="topbar"],' +
      '[data-cs-root] [class*="top_bar"],[data-cs-root] [class*="top-bar"],[data-cs-root] [class*="site-logo"],[data-cs-root] [class*="site_logo"],' +
      '[data-cs-root] [id*="header"]:not([id*="article"]):not([id*="view"]):not([id*="post"]):not([id*="cmt"]):not([id*="comment"]),' +
      '[data-cs-root] [class*="header"]:not([class*="article"]):not([class*="view"]):not([class*="post"]):not([class*="cmt"]):not([class*="comment"]):not([class*="title"]){display:none !important}';
    // 본문 안에 들어있는 화면 고정 막대(위·아래 붙는 메뉴)도 숨긴다
    keep.forEach(function(k){
      k.querySelectorAll('*').forEach(function(e){
        var pos = getComputedStyle(e).position;
        if ((pos==='fixed' || pos==='sticky') && !e.querySelector('textarea') && e.getBoundingClientRect().height < 120 && !e.querySelector('img,video,iframe')) { e.style.setProperty('display','none','important'); e.setAttribute('data-cs-hidden','1'); }
      });
    });
    document.querySelectorAll('.included-article-list,.article-list,.board_list_table,.relative_list,.notice_board').forEach(function(e){
      if (!holds(e) && !cms.some(function(x){ return x.contains(e); }) && !art.contains(e)) { e.style.setProperty('display','none','important'); e.setAttribute('data-cs-hidden','1'); }
    });
    document.body.style.setProperty('padding-top','0','important');
    document.body.style.setProperty('margin-top','0','important');
    keep.forEach(function(k){ k.style.setProperty('max-width','100%','important'); k.style.setProperty('margin-left','0','important'); k.style.setProperty('margin-right','0','important'); });
    // 정리 후 본문이 실제로 안 보이면 전부 되돌리고 원본을 보여준다
    var h = art.getBoundingClientRect().height;
    var vis = (art.innerText||'').trim().length + art.querySelectorAll('img,video,iframe').length*50;
    if (h < 40 || vis < 20 || !shown(art)) {
      st.textContent = '';
      document.querySelectorAll('[data-cs-path],[data-cs-root]').forEach(function(e){ e.removeAttribute('data-cs-path'); e.removeAttribute('data-cs-root'); });
      document.querySelectorAll('[data-cs-hidden]').forEach(function(e){ e.style.removeProperty('display'); e.removeAttribute('data-cs-hidden'); });
      window.__csArt = null;
      return 'none';
    }
    return 'ok';
  } catch(e) { return 'err'; }
})(${arr(articleSel[site].orEmpty() + genericArticle)}, $own, ${arr(commentSel[site].orEmpty() + genericComment)}, '$t', '${site.postPattern.pattern.replace("\\", "\\\\").replace("'", "\\'")}');
"""
    }

    /** 본문만 보이게 한 번 시도. 결과(성공 여부)를 알려준다 */
    fun isolateOnce(view: WebView, site: Site, title: String, done: (Boolean) -> Unit) {
        view.evaluateJavascript(isolateJs(site, title)) { r -> done(r?.contains("ok") == true) }
    }

    /** 로딩이 끝난 뒤: 늦게 붙는 댓글을 위해 몇 번 더 반복 */
    fun isolateLater(view: WebView, site: Site, title: String, stillValid: () -> Boolean) {
        val js = isolateJs(site, title)
        view.postDelayed({ if (stillValid()) view.evaluateJavascript(js, null) }, 1200)
        view.postDelayed({ if (stillValid()) view.evaluateJavascript(js, null) }, 3000)
    }

    const val FONT_HOST = "cs-font.local"

    private fun fontCss(file: String) = """
@font-face { font-family:'CsAppFont'; src:url('https://$FONT_HOST/$file'); font-weight:normal; }
@font-face { font-family:'CsAppFont'; src:url('https://$FONT_HOST/${file.removeSuffix(".ttf")}_bold.ttf'); font-weight:bold; }
body, body p, body div, body span:not([class*="icon"]):not([class*="fa-"]), body a, body li, body td, body th,
body h1, body h2, body h3, body h4, body h5, body h6, body strong, body b, body em, body label, body button, body input, body textarea
{ font-family:'CsAppFont', sans-serif !important; }
"""

    private fun styleJs(id: String, css: String): String {
        val esc = css.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
        return """(function(){var s=document.getElementById('$id'); if(!s){s=document.createElement('style'); s.id='$id'; (document.head||document.documentElement).appendChild(s);} s.textContent=`$esc`;})();"""
    }

    /** 페이지가 열릴 때마다 넣는 스크립트 */
    fun inject(view: WebView) {
        val sb = StringBuilder()
        if (Store.adBlock) sb.append(styleJs("cs-ad", AD_CSS))
        if (Store.unlock) sb.append(styleJs("cs-unlock", UNLOCK_CSS)).append(UNLOCK_JS)
        val font = Fonts.byKey(Store.font)
        if (Store.fontInWeb && font?.file != null && Fonts.exists(view.context, font.file)) {
            sb.append(styleJs("cs-font", fontCss(font.file)))
        }
        if (sb.isNotEmpty()) view.evaluateJavascript(sb.toString(), null)
    }

    private fun empty() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /** 웹뷰가 받는 요청 가로채기: 광고 막기, 앱 글꼴 내주기 */
    fun intercept(ctx: Context, req: WebResourceRequest): WebResourceResponse? {
        val host = req.url.host ?: return null
        if (host == FONT_HOST) {
            val name = req.url.lastPathSegment ?: return empty()
            return runCatching {
                WebResourceResponse("font/ttf", null, ctx.assets.open("fonts/$name")).apply {
                    responseHeaders = mapOf("Access-Control-Allow-Origin" to "*")
                }
            }.getOrElse { empty() }
        }
        if (Store.adBlock && adHosts.any { host == it.trimEnd('.') || host.endsWith(".$it") || host.endsWith(it) || (it.endsWith('.') && host.startsWith(it)) }) {
            return empty()
        }
        return null
    }
}
