// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chiaking.tv.R
import com.chiaking.tv.common.psn.PsnAuth
import com.chiaking.tv.common.psn.PsnLoginException
import com.chiaking.tv.common.psn.PsnLoginPhase
import com.chiaking.tv.common.psn.PsnTokenStore
import com.chiaking.tv.common.psn.PsnAccountException
import com.chiaking.tv.common.psn.PsnAccountError
import com.chiaking.tv.ui.components.dialogs.PsAlertDialog
import com.chiaking.tv.ui.components.TvTextField
import com.chiaking.tv.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.metallic.chiaki.lib.Holepunch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PsnLoginDialog(asExtra: Boolean = false, onDismiss: () -> Unit, onLoggedIn: (String) -> Unit)
{
	val ctx = LocalContext.current
	val scope = rememberCoroutineScope()
	var paste by remember { mutableStateOf("") }
	var error by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	var status by remember { mutableStateOf<PsnLoginPhase?>(null) }
	var loginUrl by remember { mutableStateOf<String?>(null) }
	var success by remember { mutableStateOf<String?>(null) }

	val uidFailedMsg = stringResource(R.string.psn_uid_failed)
	val badCodeMsg = stringResource(R.string.psn_bad_code)
	val signinFailedMsg = stringResource(R.string.psn_signin_failed)
	val limitMsg = stringResource(R.string.psn_extra_limit)
	val primaryMsg = stringResource(R.string.psn_extra_is_primary)
	val statusText = stringResource(
		when(status)
		{
			PsnLoginPhase.GENERATING_UID -> R.string.psn_generating_uid
			PsnLoginPhase.EXCHANGING -> R.string.psn_exchanging
			PsnLoginPhase.FETCHING_ACCOUNT -> R.string.psn_fetching_account
			PsnLoginPhase.SAVING -> R.string.psn_saving
			null -> R.string.psn_working
		}
	)

	LaunchedEffect(Unit) {
		loginUrl = withContext(Dispatchers.IO) {
			val duid = Holepunch.generateClientDeviceUid()
			if(duid.isEmpty()) null else PsnAuth.loginUrl(duid)
		}
		if(loginUrl == null)
			error = uidFailedMsg
	}

	val signedInAs = success
	if(signedInAs != null)
	{
		PsAlertDialog(
			onDismissRequest = { },
			title = { Text(stringResource(R.string.psn_signed_in_title)) },
			text = {
				Text(
					stringResource(R.string.psn_signed_in_message, signedInAs),
					style = MaterialTheme.typography.labelLarge,
					color = OnSurfaceMuted
				)
			},
			confirmButton = {
				TextButton(onClick = { onLoggedIn(signedInAs) }) { Text(stringResource(R.string.close)) }
			}
		)
		return
	}

	val scrollState = rememberScrollState()
	LaunchedEffect(busy, status) {
		if(busy)
			scrollState.animateScrollTo(scrollState.maxValue)
	}

	PsAlertDialog(
		onDismissRequest = { if(!busy) onDismiss() },
		title = { Text(stringResource(R.string.psn_login_title)) },
		text = {
			Column(
				modifier = Modifier.verticalScroll(scrollState),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(
					stringResource(R.string.psn_login_instructions),
					style = MaterialTheme.typography.labelLarge,
					color = OnSurfaceMuted
				)
				Spacer(Modifier.height(16.dp))
				val url = loginUrl
				if(url != null)
				{
					val qr = remember(url) { generateQrBitmap(url, 512) }
					if(qr != null)
						Image(
							bitmap = qr.asImageBitmap(),
							contentDescription = "PSN login QR code",
							modifier = Modifier.size(180.dp)
						)
				}
				else if(!busy)
					CircularProgressIndicator(color = PsBlueLight, modifier = Modifier.size(32.dp))
				Spacer(Modifier.height(16.dp))
				TvTextField(stringResource(R.string.psn_paste_hint), paste, enabled = !busy) { paste = it; error = null }
				if(error != null)
					Text(error!!, style = MaterialTheme.typography.bodySmall, color = WarningAmber)
				if(busy)
				{
					Spacer(Modifier.height(16.dp))
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						CircularProgressIndicator(color = PsBlueLight, modifier = Modifier.size(24.dp))
						Text(statusText, style = MaterialTheme.typography.labelLarge, color = OnSurfaceMuted)
					}
				}
			}
		},
		confirmButton = {
			TextButton(
				enabled = !busy && paste.isNotBlank(),
				onClick = {
					val code = PsnAuth.parseRedirect(paste)
					if(code == null)
					{
						error = badCodeMsg
						return@TextButton
					}
					busy = true
					status = null
					scope.launch {
						try
						{
							val accountId = PsnTokenStore.get(ctx).loginWithCode(code, asExtra) { status = it }
							success = accountId
						}
						catch(e: PsnAccountException)
						{
							error = if(e.reason == PsnAccountError.LIMIT_REACHED) limitMsg else primaryMsg
						}
						catch(e: PsnLoginException)
						{
							android.util.Log.e("PsnLoginDialog", "PSN login failed", e)
							error = uidFailedMsg
						}
						catch(e: Exception)
						{
							android.util.Log.e("PsnLoginDialog", "PSN login failed", e)
							error = e.message ?: signinFailedMsg
						}
						finally
						{
							busy = false
							status = null
						}
					}
				}
			) { Text(stringResource(R.string.psn_sign_in)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
		}
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
