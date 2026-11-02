package com.kakario.communityscrap.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Box
import com.kakario.communityscrap.Post
import com.kakario.communityscrap.Store

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

@Composable
fun ViewerScreen(post: Post?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    if (post == null) {
        Column { Bar("글 보기", onBack = onBack) }
        return
    }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    val marked = Store.bookmarks.any { it.url == post.url }

    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize()) {
        Bar(post.title, onBack = onBack) {
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
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    setupCommon()
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    webViewClient = object : WebViewClient() {
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
                            canBack = view.canGoBack()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            canBack = view.canGoBack()
                            CookieManager.getInstance().flush()
                        }
                    }
                    loadUrl(post.url)
                    web = this
                }
            },
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
