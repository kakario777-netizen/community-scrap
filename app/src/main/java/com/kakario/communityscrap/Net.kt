package com.kakario.communityscrap

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** 웹뷰(설정에서 로그인한 화면)와 같은 쿠키 저장소를 쓰는 쿠키 통 */
object WebViewCookieJar : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = CookieManager.getInstance()
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }
}

object Net {
    lateinit var userAgent: String
        private set

    lateinit var client: OkHttpClient
        private set

    fun init(ctx: Context) {
        // 웹뷰와 같은 브라우저 정보로 접속해야 로그인·보안 쿠키가 그대로 통한다
        userAgent = runCatching { WebSettings.getDefaultUserAgent(ctx) }.getOrElse {
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        client = OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder().header("User-Agent", userAgent)
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                if (req.header("Referer") == null) refererFor(req.url.host)?.let { b.header("Referer", it) }
                chain.proceed(b.build())
            }
            .build()
    }

    /** 이미지 바로 불러오기 막힌 사이트용 출처 주소 */
    fun refererFor(host: String): String? = when {
        host.contains("ruliweb") -> "https://bbs.ruliweb.com/"
        host.contains("arca") || host.contains("namu.la") -> "https://arca.live/"
        host.contains("goodgag") -> "https://www.goodgag.net/"
        else -> null
    }

    suspend fun getHtml(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(
                Request.Builder().url(url)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()
            ).execute().use { res ->
                if (!res.isSuccessful) null else res.body?.string()
            }
        }.getOrNull()
    }
}

/**
 * 보이지 않는 웹뷰로 페이지를 열어 HTML을 가져온다.
 * 바로 접속이 막히는 사이트(보안 확인 화면 등)도 실제 브라우저처럼 통과시키기 위한 예비 수단.
 */
object WebFetcher {
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun <T> load(ctx: Context, url: String, timeoutMs: Long = 20_000, accept: (String) -> T?): T? =
        withContext(Dispatchers.Main) {
            var web: WebView? = null
            var last: T? = null
            val result = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<T?> { cont ->
                    val handler = Handler(Looper.getMainLooper())
                    val w = WebView(ctx.applicationContext)
                    web = w
                    w.settings.javaScriptEnabled = true
                    w.settings.domStorageEnabled = true
                    w.settings.blockNetworkImage = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                    w.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, u: String?) {
                            // 화면이 그려질 시간을 조금 준 뒤 HTML을 꺼낸다
                            handler.postDelayed({
                                if (!cont.isActive) return@postDelayed
                                view.evaluateJavascript("document.documentElement.outerHTML") { js ->
                                    if (!cont.isActive) return@evaluateJavascript
                                    val html = runCatching { JSONTokener(js).nextValue() as? String }.getOrNull()
                                        ?: return@evaluateJavascript
                                    val r = accept(html)
                                    if (r != null) {
                                        last = r
                                        if (!(r is Collection<*> && r.isEmpty())) cont.resume(r)
                                    }
                                }
                            }, 1500)
                        }
                    }
                    cont.invokeOnCancellation { handler.post { w.stopLoading() } }
                    w.loadUrl(url)
                }
            }
            CookieManager.getInstance().flush()
            web?.destroy()
            result ?: last
        }
}
