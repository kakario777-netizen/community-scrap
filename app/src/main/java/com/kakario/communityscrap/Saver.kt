package com.kakario.communityscrap

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.InputStream

/** 이미지·영상을 갤러리(사진/커뮤모아, 동영상/커뮤모아)에 저장 */
object Saver {
    private const val FOLDER = "커뮤모아"

    /** 설정 화면에 보여줄 저장 위치 이름 */
    fun folderLabel(): String {
        val t = Store.saveTree ?: return "기본: 사진·동영상 › 커뮤모아"
        val id = runCatching { android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(t)) }.getOrNull() ?: return t
        val vol = id.substringBefore(':')
        val path = id.substringAfter(':', "")
        val root = if (vol == "primary") "내장 저장공간" else "SD카드"
        return if (path.isEmpty()) root else "$root › ${path.replace("/", " › ")}"
    }

    sealed class Result {
        data class Ok(val name: String) : Result()
        data class Fail(val why: String) : Result()
    }

    suspend fun save(ctx: Context, url: String, pageUrl: String?): Result = withContext(Dispatchers.IO) {
        runCatching {
            when {
                url.startsWith("blob:") -> Result.Fail("이 영상은 사이트가 조각내서 재생하는 방식이라 저장할 수 없어요")
                url.startsWith("data:") -> saveData(ctx, url)
                else -> saveHttp(ctx, url, pageUrl)
            }
        }.getOrElse { Result.Fail("저장하지 못했어요 (${it.message ?: "오류"})") }
    }

    private fun saveData(ctx: Context, url: String): Result {
        val mime = url.substringAfter("data:").substringBefore(';').ifBlank { "image/png" }
        val bytes = Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)
        return write(ctx, bytes.inputStream(), mime, "img")
    }

    private fun saveHttp(ctx: Context, url: String, pageUrl: String?): Result {
        val b = Request.Builder().url(url)
        if (pageUrl != null) b.header("Referer", pageUrl)
        Net.client.newCall(b.build()).execute().use { res ->
            if (!res.isSuccessful) return Result.Fail("저장하지 못했어요 (응답 ${res.code})")
            val body = res.body ?: return Result.Fail("저장하지 못했어요 (빈 응답)")
            var mime = body.contentType()?.let { "${it.type}/${it.subtype}" }.orEmpty()
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                val ext = MimeTypeMap.getFileExtensionFromUrl(url.substringBefore('?')).lowercase()
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
            }
            val base = Uri.parse(url).lastPathSegment?.substringBeforeLast('.')
                ?.replace(Regex("[^\\w가-힣-]"), "")?.take(40)?.ifBlank { null } ?: "img"
            return write(ctx, body.byteStream(), mime, base)
        }
    }

    private fun write(ctx: Context, input: InputStream, mime: String, base: String): Result {
        val video = mime.startsWith("video/")
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (video) "mp4" else "jpg"
        val name = "${base}_${System.currentTimeMillis() % 1_000_000}.$ext"

        // 설정에서 고른 폴더가 있으면 그곳에 저장
        Store.saveTree?.let { t ->
            val tree = Uri.parse(t)
            val r = ctx.contentResolver
            val dir = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                tree, android.provider.DocumentsContract.getTreeDocumentId(tree)
            )
            val doc = runCatching { android.provider.DocumentsContract.createDocument(r, dir, mime, name) }.getOrNull()
                ?: return Result.Fail("고른 폴더에 저장하지 못했어요. 설정에서 저장 폴더를 다시 골라 주세요.")
            r.openOutputStream(doc)?.use { out -> input.copyTo(out) }
            return Result.Ok(name)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val coll = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val dir = (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/" + FOLDER
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, dir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val uri = r.insert(coll, cv) ?: return Result.Fail("저장 공간을 열지 못했어요")
            r.openOutputStream(uri)?.use { out -> input.copyTo(out) }
            cv.clear(); cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
            r.update(uri, cv, null, null)
        } else {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStoragePublicDirectory(
                if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            )
            val dir = File(root, FOLDER).apply { mkdirs() }
            val f = File(dir, name)
            f.outputStream().use { input.copyTo(it) }
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf(mime), null)
        }
        return Result.Ok(name)
    }
}
