@file:OptIn(ExperimentalFoundationApi::class)

package com.kakario.communityscrap.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kakario.communityscrap.Post
import com.kakario.communityscrap.Site
import com.kakario.communityscrap.Store

val Line = Color(0xFFECECEC)
val Sub = Color(0xFF8A8A8A)

fun Site.c() = Color(color)

@Composable
fun Bar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                title,
                Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
        HorizontalDivider(color = Line)
    }
}

@Composable
fun SiteTag(site: Site) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(site.c()))
        Spacer(Modifier.width(4.dp))
        Text(site.label, fontSize = 12.sp, color = site.c(), fontWeight = FontWeight.SemiBold)
    }
}

/** 숫자를 짧게: 12345 → 1.2만 */
fun short(n: Int): String = when {
    n >= 10000 -> String.format("%.1f만", n / 10000.0).replace(".0만", "만")
    n >= 1000 -> String.format("%.1f천", n / 1000.0).replace(".0천", "천")
    else -> n.toString()
}

/** 왼쪽에 붙는 말머리·주제 태그 */
@Composable
fun TopicTag(text: String, color: Color) {
    Box(
        Modifier.padding(top = 2.dp).widthIn(max = 76.dp).clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PostRow(p: Post, showSite: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val read = p.key in Store.readKeys
    val marked = Store.bookmarks.any { it.url == p.url }
    val titleColor = if (read) Color(0xFFA6A6A6) else Color(0xFF1A1A1A)
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    p.category?.let {
                        TopicTag(it, if (read) Color(0xFFA6A6A6) else Color(0xFF1E6BE6))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        buildAnnotatedString {
                            append(p.title)
                            p.comments?.takeIf { it > 0 }?.let {
                                withStyle(SpanStyle(color = Color(0xFFE8590C), fontWeight = FontWeight.Bold, fontSize = 13.sp)) {
                                    append("  [$it]")
                                }
                            }
                        },
                        fontSize = 15.5.sp,
                        lineHeight = 21.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = titleColor,
                        fontWeight = if (read) FontWeight.Normal else FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showSite) {
                        SiteTag(p.site)
                        Spacer(Modifier.width(8.dp))
                    }
                    val meta = listOfNotNull(
                        p.info,
                        p.time,
                        p.views?.let { "조회 ${short(it)}" },
                        p.likes?.takeIf { it > 0 }?.let { "추천 ${short(it)}" },
                    ).joinToString("  ·  ")
                    if (meta.isNotEmpty()) {
                        Text(meta, Modifier.weight(1f, fill = false), fontSize = 12.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (marked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.Bookmark, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (p.thumb != null) {
                AsyncImage(
                    model = p.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2F2F2)),
                )
            }
        }
        HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 14.dp))
    }
}
