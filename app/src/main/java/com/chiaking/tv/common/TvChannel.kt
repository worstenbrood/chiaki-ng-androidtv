// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log
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
				val builder = TvContract.Channels.Builder()
					.setDisplayName("Chiaki TV")
					.setDescription("Stream your registered PlayStation consoles")
					.setInternalIntent(Intent(context, MainActivity::class.java))
					.setAppIconUri(resUri(context, R.mipmap.ic_launcher))
					.setBannerUri(resUri(context, R.drawable.tv_banner))
				val uri = resolver.insert(TvContract.Channels.CONTENT_URI, builder.build()) ?: return
				channelId = ContentUris.parseId(uri)
				preferences.tvChannelId = channelId
			}

			resolver.delete(TvContract.Programs.CONTENT_URI,
				TvContract.Programs.FIELD_CHANNEL_ID + "=?", arrayOf(channelId.toString()))

			for(host in hosts)
			{
				val title = host.serverNickname?.takeIf { it.isNotBlank() }
					?: if(host.target.isPS5) "PlayStation 5" else "PlayStation 4"
				val intent = Intent(context, MainActivity::class.java)
					.putExtra(MainActivity.EXTRA_AUTO_CONNECT_MAC, host.serverMac.toString())
					.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
				val program = TvContract.Programs.Builder()
					.setChannelId(channelId)
					.setTitle(title)
					.setDescription(if(host.target.isPS5) "Connect to PS5" else "Connect to PS4")
					.setInternalIntent(intent)
					.setPosterUri(resUri(context, R.mipmap.ic_launcher))
					.setPosterAspectRatio(2, 3)
					.build()
				resolver.insert(TvContract.Programs.CONTENT_URI, program)
			}
		}
		catch(e: Exception)
		{
			Log.e("TvChannel", "Failed to sync TV channel", e)
		}
	}
}
