package com.chiaking.tv.ui.screens.stream

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.session.CoopAccountChoice
import com.chiaking.tv.session.StreamSession
import com.chiaking.tv.ui.components.TvFocusCard
import com.chiaking.tv.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chiaking.tv.common.psn.PsnHttpClient
import java.net.URL

/** A Compose overlay keeps the existing video surface and Remote Play session attached. */
@Composable
fun CoopAccountPicker(choice: CoopAccountChoice, session: StreamSession) {
    BackHandler { session.cancelCoopAccountChoice(choice.id) }
    val focus = remember(choice.id) { List(choice.accounts.size + 1) { FocusRequester() } }
    LaunchedEffect(choice.id, choice.selectedIndex) {
        // Retry after composition has attached the new cards, just like the stream HUD.
        repeat(5) {
            try {
                focus.getOrNull(choice.selectedIndex)?.requestFocus()
                return@LaunchedEffect
            } catch(_: IllegalStateException) { delay(50) }
        }
    }
    Box(Modifier.fillMaxSize().background(VideoBlack.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.coop_choose_account, choice.slot + 1),
                style = MaterialTheme.typography.headlineSmall, color = OnSurfaceLight)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.coop_choose_account_hint),
                style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
            if(choice.accounts.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.coop_account_required, choice.slot + 1),
                    style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 36.dp, vertical = 32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                choice.accounts.forEachIndexed { index, account ->
                    key(account.rid) {
                        TvFocusCard(
                            focusRequester = focus[index],
                            containerColor = if(account.available) SurfaceCard else SurfaceFrosted,
                            onFocusChange = { if(it) session.focusCoopAccount(choice.id, index) },
                            onClick = if(account.available) ({ session.chooseCoopAccount(choice.id, account.rid) }) else null
                        ) {
                            Column(Modifier.width(200.dp).height(210.dp).padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                PsnAccountAvatar(account.avatarUrl, Modifier.size(72.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(account.onlineId.ifBlank { stringResource(R.string.psn_extra_account, index + 1) },
                                    style = MaterialTheme.typography.titleMedium, color = OnSurfaceLight,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(8.dp))
                                Text(if(account.available) account.rid else stringResource(R.string.coop_account_unavailable),
                                    style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                TvFocusCard(focusRequester = focus.last(),
                    onFocusChange = { if(it) session.focusCoopAccount(choice.id, choice.accounts.size) },
                    onClick = { session.cancelCoopAccountChoice(choice.id) }) {
                    Column(Modifier.width(200.dp).height(210.dp).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Close, null, Modifier.size(48.dp), tint = OnSurfaceMuted)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium, color = OnSurfaceLight)
                    }
                }
            }
        }
    }
}

@Composable
private fun PsnAccountAvatar(url: String, modifier: Modifier = Modifier) {
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
