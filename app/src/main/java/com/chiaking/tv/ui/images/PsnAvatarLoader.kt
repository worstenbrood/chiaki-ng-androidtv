package com.chiaking.tv.ui.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.chiaking.tv.common.psn.PsnHttpClient
import java.net.URL

/** Bounded image loading shared by account UI; never sends account credentials to image hosts. */
internal object PsnAvatarLoader {
private val avatarHttp = PsnHttpClient()

private val avatarCache = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** Public avatar images only: never forward PSN credentials to an image host. */
fun load(url: String): Bitmap? {
    if(url.isBlank()) return null
    avatarCache.get(url)?.let { return it }
    return try {
        // Sony's profile API can return an HTTP CDN URL; use HTTPS for the image.
        val safeUrl = if(url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
        val source = URL(safeUrl)
        if(source.protocol != "https" || source.userInfo != null) return null
        val bytes = avatarHttp.downloadImage(source.toString())
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if(options.outWidth !in 1..8192 || options.outHeight !in 1..8192) return null
        options.inSampleSize = 1
        while(maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.also { avatarCache.put(url, it) }
    } catch(_: Exception) { null }
}

}
