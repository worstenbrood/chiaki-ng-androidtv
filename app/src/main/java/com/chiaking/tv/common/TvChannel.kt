// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.chiaking.tv.MainActivity
import com.chiaking.tv.R

object TvChannel
{
	private val TV_PACKAGES = listOf("com.google.android.tv", "com.android.tv", "com.google.android.leanbacklauncher")

	private fun resUri(context: Context, resId: Int): Uri =
		Uri.parse("android.resource://${context.packageName}/$resId")

	private fun previewVideoUri(context: Context, ps5: Boolean): Uri?
	{
		return try
		{
			val dir = java.io.File(context.cacheDir, "tv")
			dir.mkdirs()
			val dst = java.io.File(dir, if(ps5) "preview_ps5.mp4" else "preview_ps4.mp4")
			if(!dst.exists() || dst.length() == 0L)
				context.resources.openRawResource(
					if(ps5) R.raw.tv_preview_ps5 else R.raw.tv_preview_ps4).use { input ->
					dst.outputStream().use { out -> input.copyTo(out) }
				}
			val uri = androidx.core.content.FileProvider.getUriForFile(context,
				"${context.packageName}.fileprovider", dst)
			for(pkg in TV_PACKAGES)
			{
				try
				{
					context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
				}
				catch(_: Exception) {}
			}
			uri
		}
		catch(e: Exception)
		{
			Log.e("TvChannel", "Failed to prepare preview video", e)
			null
		}
	}

	fun sync(context: Context, preferences: Preferences, hosts: List<RegisteredHost>)
	{
		try
		{
			val resolver = context.contentResolver
			var channelId = preferences.tvChannelId
			if(channelId <= 0L)
			{
				val builder = Channel.Builder()
					.setDisplayName("Chiaki TV")
					.setDescription("Stream your registered PlayStation consoles")
					.setType(TvContractCompat.Channels.TYPE_PREVIEW)
				val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, builder.build().toContentValues()) ?: return
				channelId = ContentUris.parseId(uri)
				preferences.tvChannelId = channelId
			}

			resolver.delete(TvContractCompat.PreviewPrograms.CONTENT_URI,
				TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID + "=?", arrayOf(channelId.toString()))

			for(host in hosts)
			{
				val previewUri = previewVideoUri(context, host.target.isPS5)
				val title = host.serverNickname?.takeIf { it.isNotBlank() }
					?: if(host.target.isPS5) "PlayStation 5" else "PlayStation 4"
				val intent = Intent(context, MainActivity::class.java)
					.putExtra(MainActivity.EXTRA_AUTO_CONNECT_MAC, host.serverMac.toString())
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
				val program = PreviewProgram.Builder()
					.setChannelId(channelId)
					.setType(TvContractCompat.PreviewPrograms.TYPE_GAME)
					.setTitle(title)
					.setDescription(if(host.target.isPS5) "Connect to PS5" else "Connect to PS4")
					.setIntent(intent)
					.setPosterArtUri(resUri(context, R.mipmap.ic_launcher))
					.setPosterArtAspectRatio(2)
					.apply { if(previewUri != null) setPreviewVideoUri(previewUri) }
					.build()
				resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues())
			}
		}
		catch(e: Exception)
		{
			Log.e("TvChannel", "Failed to sync TV channel", e)
		}
	}
}
