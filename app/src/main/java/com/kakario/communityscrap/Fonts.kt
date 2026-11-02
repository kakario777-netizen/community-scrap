@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.kakario.communityscrap

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

data class AppFont(val key: String, val label: String, val file: String?)

/** 고를 수 있는 글꼴. 파일은 빌드할 때 받아서 assets/fonts 에 넣는다 */
object Fonts {
    val all = listOf(
        AppFont("system", "기본 글꼴", null),
        AppFont("nanumgothic", "나눔고딕", "nanumgothic.ttf"),
        AppFont("gowundodum", "고운돋움", "gowundodum.ttf"),
        AppFont("nanummyeongjo", "나눔명조", "nanummyeongjo.ttf"),
    )

    private var cached: Set<String>? = null

    private fun files(ctx: Context): Set<String> =
        cached ?: (runCatching { ctx.assets.list("fonts")?.toSet() }.getOrNull() ?: emptySet()).also { cached = it }

    fun exists(ctx: Context, file: String) = file in files(ctx)

    fun byKey(key: String) = all.firstOrNull { it.key == key }

    /** 실제로 들어있는 글꼴만 */
    fun available(ctx: Context) = all.filter { it.file == null || exists(ctx, it.file) }

    fun family(ctx: Context, key: String): FontFamily {
        val f = byKey(key)?.file ?: return FontFamily.Default
        if (!exists(ctx, f)) return FontFamily.Default
        val bold = f.removeSuffix(".ttf") + "_bold.ttf"
        val list = mutableListOf(Font(f.let { "fonts/$it" }, ctx.assets, FontWeight.Normal))
        if (exists(ctx, bold)) list += Font("fonts/$bold", ctx.assets, FontWeight.Bold)
        return FontFamily(list)
    }
}
