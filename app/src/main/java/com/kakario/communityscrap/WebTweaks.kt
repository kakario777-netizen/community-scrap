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
