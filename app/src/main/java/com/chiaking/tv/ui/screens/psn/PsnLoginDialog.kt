// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens.psn

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.common.psn.PsnLoginPhase
import com.chiaking.tv.ui.components.TvTextField
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import com.chiaking.tv.ui.theme.OnSurfaceMuted
import com.chiaking.tv.ui.theme.PsBlueLight
import com.chiaking.tv.ui.theme.WarningAmber
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

@Composable
fun PsnLoginDialog(onDismiss: () -> Unit, onLoggedIn: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val controller = remember { PsnLoginController(context, scope) }
    DisposableEffect(controller) { onDispose { controller.close() } }
    val state by controller.state.collectAsState()
    var paste by remember { mutableStateOf("") }
    var manualEntry by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val bodyMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    LaunchedEffect(manualEntry) { scroll.scrollTo(0) }
    LaunchedEffect(state.busy, state.phase) { if(state.busy) scroll.animateScrollTo(scroll.maxValue) }

    val accountId = state.success
    if(accountId != null) {
        PsAlertDialog(
            onDismissRequest = { onLoggedIn(accountId) },
            title = { Text(stringResource(R.string.psn_signed_in_title)) },
            text = { Text(stringResource(R.string.psn_signed_in_message, accountId),
                style = MaterialTheme.typography.labelLarge, color = OnSurfaceMuted) },
            confirmButton = { TextButton(onClick = { onLoggedIn(accountId) }) { Text(stringResource(R.string.close)) } }
        )
        return
    }

    val statusText = stringResource(when(state.phase) {
        PsnLoginPhase.GENERATING_UID -> R.string.psn_generating_uid
        PsnLoginPhase.EXCHANGING -> R.string.psn_exchanging
        PsnLoginPhase.FETCHING_ACCOUNT -> R.string.psn_fetching_account
        PsnLoginPhase.SAVING -> R.string.psn_saving
        null -> R.string.psn_working
    })
    PsAlertDialog(
        onDismissRequest = { if(!state.busy) onDismiss() },
        title = { Text(stringResource(R.string.psn_login_title)) },
        text = {
            Column(Modifier.heightIn(max = bodyMaxHeight).verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                if(manualEntry) {
                    Text(stringResource(R.string.psn_phone_manual), style = MaterialTheme.typography.labelLarge, color = OnSurfaceMuted)
                    TvTextField(stringResource(R.string.psn_paste_hint), paste, enabled = !state.busy) { paste = it }
                    TextButton(onClick = { manualEntry = false }, enabled = !state.busy) {
                        Text(stringResource(R.string.psn_show_qr))
                    }
                } else {
                    Text(stringResource(if(!state.phoneUnavailable) R.string.psn_phone_instructions else R.string.psn_login_instructions),
                        style = MaterialTheme.typography.labelLarge, color = OnSurfaceMuted)
                    Spacer(Modifier.height(16.dp))
                    val url = state.phoneUrl ?: state.loginUrl.takeIf { state.phoneUnavailable }
                    if(url != null) {
                        val qr = remember(url) { generateQrBitmap(url, 512) }
                        if(qr != null) Image(qr.asImageBitmap(), stringResource(R.string.psn_phone_qr), Modifier.size(160.dp))
                        if(state.phoneUrl != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.psn_phone_expiry), style = MaterialTheme.typography.bodySmall, color = OnSurfaceMuted)
                        }
                    } else if(!state.busy && state.error == null) CircularProgressIndicator(color = PsBlueLight, modifier = Modifier.size(32.dp))
                    if(state.phoneUnavailable) Text(stringResource(R.string.psn_phone_unavailable),
                        style = MaterialTheme.typography.bodySmall, color = WarningAmber)
                }
                state.error?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = WarningAmber) }
                if(state.busy) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = PsBlueLight, modifier = Modifier.size(24.dp))
                        Text(statusText, style = MaterialTheme.typography.labelLarge, color = OnSurfaceMuted)
                    }
                }
            }
        },
        confirmButton = {
            if(manualEntry) {
                TextButton(enabled = !state.busy && state.loginUrl != null && paste.isNotBlank(),
                    onClick = { controller.submitManual(paste) }) { Text(stringResource(R.string.psn_sign_in)) }
            } else {
                TextButton(enabled = !state.busy, onClick = { manualEntry = true }) {
                    Text(stringResource(R.string.psn_manual_entry))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun generateQrBitmap(text: String, size: Int): Bitmap?
{
    return try
    {
        val matrix = MultiFormatWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for(x in 0 until size)
            for(y in 0 until size)
                bmp.setPixel(x, y, if(matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bmp
    }
    catch(e: Exception)
    {
        android.util.Log.e("PsnLoginDialog", "QR generation failed", e)
        null
    }
}
