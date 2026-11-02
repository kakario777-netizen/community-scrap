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

@Composable
fun PostRow(p: Post, showSite: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val read = p.key in Store.readKeys
    val marked = Store.bookmarks.any { it.url == p.url }
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    p.title,
                    fontSize = 15.5.sp,
                    lineHeight = 21.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (read) Color(0xFFA6A6A6) else Color(0xFF1A1A1A),
                    fontWeight = if (read) FontWeight.Normal else FontWeight.Medium,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showSite) {
                        SiteTag(p.site)
                        Spacer(Modifier.width(8.dp))
                    }
                    p.comments?.let {
                        Text("댓글 $it", fontSize = 12.sp, color = Sub)
                        Spacer(Modifier.width(8.dp))
                    }
                    p.info?.let {
                        Text(it, fontSize = 12.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 16.dp))
    }
}
