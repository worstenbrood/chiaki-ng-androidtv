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
	private fun resUri(context: Context, resId: Int): Uri =
		Uri.parse("android.resource://${context.packageName}/$resId")

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
