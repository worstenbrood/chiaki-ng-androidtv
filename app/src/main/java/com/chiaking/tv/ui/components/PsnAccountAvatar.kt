package com.chiaking.tv.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.chiaking.tv.common.psn.PsnHttpClient
import com.chiaking.tv.ui.theme.PsBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun PsnAccountAvatar(url: String, modifier: Modifier = Modifier) {
    val avatar by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { loadAvatar(url) }
    }
    val image = avatar
    if(image != null) Image(image.asImageBitmap(), null, modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    else Icon(Icons.Default.Person, null, modifier, tint = PsBlueLight)
}

private val avatarHttp = PsnHttpClient()

private val avatarCache = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** Public avatar images only: never forward PSN credentials to an image host. */
private fun loadAvatar(url: String): Bitmap? {
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
