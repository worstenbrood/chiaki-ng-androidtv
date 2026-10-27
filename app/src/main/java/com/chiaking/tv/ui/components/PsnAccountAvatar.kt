package com.chiaking.tv.ui.components

import com.chiaking.tv.ui.images.PsnAvatarLoader
import android.graphics.Bitmap
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
import com.chiaking.tv.ui.theme.PsBlueLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PsnAccountAvatar(url: String, modifier: Modifier = Modifier) {
    val avatar by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { PsnAvatarLoader.load(url) }
    }
    val image = avatar
    if(image != null) Image(image.asImageBitmap(), null, modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    else Icon(Icons.Default.Person, null, modifier, tint = PsBlueLight)
}
