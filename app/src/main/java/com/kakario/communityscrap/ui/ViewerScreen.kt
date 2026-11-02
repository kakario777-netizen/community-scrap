package com.kakario.communityscrap.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kakario.communityscrap.Post
import com.kakario.communityscrap.Current
import com.kakario.communityscrap.Site
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kakario.communityscrap.Saver
import com.kakario.communityscrap.Store
import com.kakario.communityscrap.WebTweaks
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONTokener

@SuppressLint("SetJavaScriptEnabled")
fun WebView.setupCommon() {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
}

/** 눌린 자리에 있는 이미지·영상 주소 찾기 (덮개가 씌워져 있어도 아래까지 확인) */
private fun pickMediaJs(x: Float, y: Float) = """
(function(px,py){
  var r = window.devicePixelRatio || 1; var x = px / r, y = py / r;
  function orig(img){
    var a = img.closest && img.closest('a');
    if (a && a.href && /(\.(jpe?g|png|gif|webp|mp4|webm)(\?|$))|type=orig/i.test(a.href)) return a.href;
    return img.getAttribute('data-original') || img.currentSrc || img.src || img.getAttribute('data-src') || '';
  }
  function vsrc(v){ var s = v.currentSrc || v.src; if (!s) { var so = v.querySelector('source'); if (so) s = so.src; } return s || ''; }
  var els = document.elementsFromPoint(x, y);
  for (var i = 0; i < els.length; i++) {
    var e = els[i];
    if (e.tagName === 'IMG') { var u = orig(e); if (u) return u; }
    if (e.tagName === 'VIDEO') { var v = vsrc(e); if (v) return v; }
    var bg = getComputedStyle(e).backgroundImage;
    if (bg && bg.indexOf('url(') === 0) return bg.slice(4, -1).replace(/["']/g, '');
    var inner = e.querySelector && e.querySelector('img, video');
    if (inner && e.getBoundingClientRect().width < window.innerWidth * 1.05 && e.getBoundingClientRect().height < window.innerHeight) {
      return inner.tagName === 'VIDEO' ? vsrc(inner) : orig(inner);
    }
  }
  return '';
})($x, $y)
"""

/** 글 안의 큰 이미지·영상 주소 전부 */
private const val ALL_MEDIA_JS = """
(function(){
  var out = [], seen = {};
  function add(u){ if (u && !seen[u] && u.indexOf('data:') !== 0) { seen[u] = 1; out.push(u); } }
  document.querySelectorAll('img').forEach(function(img){
    var w = img.naturalWidth || img.width, h = img.naturalHeight || img.height;
    if (w < 200 && h < 200) return;
    var a = img.closest('a'), u = '';
    if (a && a.href && /(\.(jpe?g|png|gif|webp|mp4|webm)(\?|$))|type=orig/i.test(a.href)) u = a.href;
    add(u || img.getAttribute('data-original') || img.currentSrc || img.src);
  });
  document.querySelectorAll('video').forEach(function(v){
    var s = v.currentSrc || v.src; if (!s) { var so = v.querySelector('source'); if (so) s = so.src; }
    add(s);
  });
  return JSON.stringify(out);
})()
"""

@Composable
fun ViewerScreen(start: Post?, list: List<Post>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (start == null) {
        Column { Bar("글 보기", onBack = onBack) }
        return
    }
    var post by remember { mutableStateOf<Post>(start) }
    var clearNext by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    val idx = list.indexOfFirst { it.url == post.url }
    val prev = if (idx > 0) list[idx - 1] else null
    val next = if (idx >= 0 && idx < list.size - 1) list[idx + 1] else null
    var progress by remember { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }   // 길게 눌러 찾은 주소
    var allMedia by remember { mutableStateOf<List<String>?>(null) }
    // 본문만 보이게 정리될 때까지 원래 페이지를 가려 둔다 (번쩍임 방지)
    var covered by remember { mutableStateOf(Store.isolate) }
    var isoOk by remember { mutableStateOf(false) }
    var loadToken by remember { mutableIntStateOf(0) }
    val marked = Store.bookmarks.any { it.url == post.url }

    fun go(p: Post) {
        post = p
        Current.post = p
        Store.markRead(p)
        clearNext = true
        web?.loadUrl(p.url)
    }

    fun saveOne(url: String) {
        val page = web?.url ?: post.url
        scope.launch {
            when (val r = Saver.save(ctx, url, page)) {
                is Saver.Result.Ok -> Toast.makeText(ctx, "저장했어요: ${r.name}", Toast.LENGTH_SHORT).show()
                is Saver.Result.Fail -> Toast.makeText(ctx, r.why, Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize()) {
        Bar(post.title, onBack = onBack) {
            IconButton(onClick = {
                web?.evaluateJavascript(ALL_MEDIA_JS) { js ->
                    val list = runCatching {
                        val s = JSONTokener(js).nextValue() as String
                        val arr = JSONArray(s)
                        (0 until arr.length()).map { arr.getString(it) }
                    }.getOrDefault(emptyList())
                    if (list.isEmpty()) Toast.makeText(ctx, "저장할 이미지가 없어요", Toast.LENGTH_SHORT).show()
                    else allMedia = list
                }
            }) { Icon(Icons.Filled.Download, "이미지 모두 저장") }
            IconButton(onClick = { Store.toggleBookmark(post) }) {
                Icon(
                    if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    "북마크",
                    tint = if (marked) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
            }
            IconButton(onClick = { web?.reload() }) { Icon(Icons.Filled.Refresh, "새로고침") }
            IconButton(onClick = {
                val u = web?.url ?: post.url
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
            }) { Icon(Icons.Filled.OpenInBrowser, "브라우저로 열기") }
        }
        Box(Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.TopStart) {
            if (progress in 1..99) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    setupCommon()
                    var tx = 0f
                    var ty = 0f
                    setOnTouchListener { _, e ->
                        if (e.actionMasked == MotionEvent.ACTION_DOWN) { tx = e.x; ty = e.y }
                        false
                    }
                    setOnLongClickListener { v ->
                        val w = v as WebView
                        val hit = w.hitTestResult
                        val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                            hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                        w.evaluateJavascript(pickMediaJs(tx, ty)) { js ->
                            val u = runCatching { JSONTokener(js).nextValue() as? String }.getOrNull()
                                ?: (if (isImage) hit.extra else null)
                            if (!u.isNullOrBlank()) pending = u
                        }
                        // 이미지 위에서는 기본 메뉴 대신 저장 창만 띄운다
                        isImage
                    }
                    // 본문 정리가 실제 화면에 그려진 뒤에야 가림막을 걷는다 (걷는 순간 옛 화면이 보이지 않게)
                    fun reveal(view: WebView) {
                        val token = loadToken
                        view.postVisualStateCallback(token.toLong(), object : WebView.VisualStateCallback() {
                            override fun onComplete(requestId: Long) {
                                view.postDelayed({ if (token == loadToken && isoOk) covered = false }, 60)
                            }
                        })
                    }
                    // 정리가 안 되면 조금 뒤 다시 시도, 끝내 안 되면 원본을 보여준다
                    fun tryIsolate(view: WebView, url: String?, left: Int) {
                        val token = loadToken
                        val site = Site.forUrl(url ?: post.url) ?: post.site
                        WebTweaks.isolateOnce(view, site, post.title) { ok ->
                            if (token != loadToken) return@isolateOnce
                            if (ok) { isoOk = true; reveal(view) }
                            else if (left > 0) view.postDelayed({ if (token == loadToken && !isoOk && !showOriginal) tryIsolate(view, url, left - 1) }, 500)
                            else covered = false
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                            if (newProgress > 30) WebTweaks.inject(view)
                            if (newProgress >= 45 && covered && !isoOk && !showOriginal) {
                                val site = Site.forUrl(view.url ?: post.url) ?: post.site
                                val token = loadToken
                                WebTweaks.isolateOnce(view, site, post.title) { ok ->
                                    if (ok && token == loadToken && !isoOk) { isoOk = true; reveal(view) }
                                }
                            }
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            WebTweaks.intercept(c, request)

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            if (u.scheme == "http" || u.scheme == "https") return false
                            // 앱 호출 주소(카톡 공유 등)는 해당 앱으로
                            runCatching {
                                c.startActivity(Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME))
                            }
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            WebTweaks.inject(view)
                            isoOk = false
                            covered = Store.isolate && !showOriginal
                            val token = ++loadToken
                            // 너무 오래 걸리면 그냥 보여준다
                            view.postDelayed({ if (token == loadToken) covered = false }, 8000)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            WebTweaks.inject(view)
                            if (Store.isolate && !showOriginal) {
                                val site = Site.forUrl(url ?: post.url) ?: post.site
                                if (!isoOk) tryIsolate(view, url, 4)
                                val token = loadToken
                                WebTweaks.isolateLater(view, site, post.title) { token == loadToken && !showOriginal && Store.isolate }
                            } else covered = false
                            if (clearNext) { view.clearHistory(); clearNext = false }
                            CookieManager.getInstance().flush()
                        }
                    }
                    loadUrl(post.url)
                    web = this
                }
            },
        )
        if (covered) {
            Box(Modifier.fillMaxSize().background(Pal.cur.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
        }
        HorizontalDivider(color = Line)
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { prev?.let { go(it) } }, enabled = prev != null, modifier = Modifier.weight(1f)) {
                Text("‹  이전 글", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = {
                showOriginal = !showOriginal
                web?.reload()
            }) {
                Text(if (showOriginal) "본문만" else "원본", fontSize = 13.sp, color = Sub)
            }
            TextButton(onClick = { next?.let { go(it) } }, enabled = next != null, modifier = Modifier.weight(1f)) {
                Text("다음 글  ›", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    pending?.let { url ->
        val video = Regex("""\.(mp4|webm|mov|m4v)(\?|$)""", RegexOption.IGNORE_CASE).containsMatchIn(url)
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (video) "영상 저장" else "이미지 저장") },
            text = { Text("갤러리의 '커뮤모아' 폴더에 저장할까요?") },
            confirmButton = { TextButton(onClick = { pending = null; saveOne(url) }) { Text("저장") } },
            dismissButton = {
                TextButton(onClick = {
                    pending = null
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text("원본 열기") }
            },
        )
    }

    allMedia?.let { list ->
        AlertDialog(
            onDismissRequest = { allMedia = null },
            title = { Text("모두 저장") },
            text = { Text("이 글의 이미지·영상 ${list.size}개를 저장할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    allMedia = null
                    val page = web?.url ?: post.url
                    scope.launch {
                        var ok = 0
                        list.forEach { if (Saver.save(ctx, it, page) is Saver.Result.Ok) ok++ }
                        Toast.makeText(ctx, "${list.size}개 중 ${ok}개 저장했어요", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { allMedia = null }) { Text("취소") } },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            web?.apply { stopLoading(); destroy() }
            web = null
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Sub) }
}
