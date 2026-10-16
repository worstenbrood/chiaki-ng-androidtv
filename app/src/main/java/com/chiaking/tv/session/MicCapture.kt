// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import com.chiaking.tv.common.AudioDevices
import com.chiaking.tv.common.Preferences

/**
 * Streams microphone PCM to the console: reads 10 ms stereo 48 kHz frames from an
 * [AudioRecord] (VOICE_COMMUNICATION source so the platform AEC/NS/AGC can operate on it)
 * and hands each frame to [feed], which forwards it to the native opus mic sender.
 */
class MicCapture(
	private val context: Context,
	private val preferences: Preferences,
	private val feed: (pcm: ShortArray) -> Unit
)
{
	companion object
	{
		private const val TAG = "MicCapture"
		const val SAMPLE_RATE = 48000
		const val FRAME_SAMPLES = 480
		const val CHANNELS = 2
		const val FRAME_SHORTS = FRAME_SAMPLES * CHANNELS

		fun hasPermission(context: Context): Boolean =
			ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
				PackageManager.PERMISSION_GRANTED
	}

	@Volatile private var running = false
	private var thread: Thread? = null
	private var record: AudioRecord? = null
	private val effects = mutableListOf<AudioEffect>()

	val active: Boolean get() = running

	fun start(): Boolean
	{
		if(running)
			return true
		if(!hasPermission(context))
			return false
		val record = openRecord() ?: return false
		this.record = record
		running = true
		thread = Thread { captureLoop(record) }.apply {
			priority = Thread.NORM_PRIORITY + 2
			name = "MicCapture"
			start()
		}
		return true
	}

	fun stop()
	{
		running = false
		thread?.let {
			try { it.join(1000) } catch(e: InterruptedException) { }
		}
		thread = null
		releaseRecord()
	}

	private fun captureLoop(record: AudioRecord)
	{
		val pcm = ShortArray(FRAME_SHORTS)
		while(running)
		{
			var read = 0
			while(read < pcm.size && running)
			{
				val r = record.read(pcm, read, pcm.size - read)
				if(r <= 0)
					break
				read += r
			}
			if(!running)
				break
			if(read < pcm.size)
				continue
			applyGain(pcm)
			feed(pcm)
		}
	}

	private fun applyGain(pcm: ShortArray)
	{
		val gain = preferences.micGain / 100f
		if(gain == 1f)
			return
		for(i in pcm.indices)
		{
			val v = pcm[i] * gain
			pcm[i] = (if(v > Short.MAX_VALUE) Short.MAX_VALUE
				else if(v < Short.MIN_VALUE) Short.MIN_VALUE
				else v.toInt()).toShort()
		}
	}

	private fun openRecord(): AudioRecord?
	{
		return try
		{
			val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
			if(minBuf <= 0)
				return null
			val record = AudioRecord(
				MediaRecorder.AudioSource.VOICE_COMMUNICATION,
				SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
				maxOf(minBuf, FRAME_SHORTS * 2 * 8))
			if(record.state != AudioRecord.STATE_INITIALIZED)
			{
				record.release()
				return null
			}
			resolveInputDevice()?.let { record.setPreferredDevice(it) }
			attachEffects(record.audioSessionId)
			record.startRecording()
			if(record.recordingState != AudioRecord.RECORDSTATE_RECORDING)
			{
				releaseEffects()
				record.release()
				return null
			}
			record
		}
		catch(e: Exception)
		{
			Log.e(TAG, "Failed to open microphone", e)
			null
		}
	}

	private fun resolveInputDevice(): AudioDeviceInfo? =
		AudioDevices.resolveInputDevice(context, preferences.micDeviceName)

	private fun attachEffects(sessionId: Int)
	{
		if(preferences.micAec && AcousticEchoCanceler.isAvailable())
			AcousticEchoCanceler.create(sessionId)?.let { it.enabled = true; effects.add(it) }
		if(preferences.micNs && NoiseSuppressor.isAvailable())
			NoiseSuppressor.create(sessionId)?.let { it.enabled = true; effects.add(it) }
		if(preferences.micAgc && AutomaticGainControl.isAvailable())
			AutomaticGainControl.create(sessionId)?.let { it.enabled = true; effects.add(it) }
	}

	private fun releaseEffects()
	{
		effects.forEach { it.release() }
		effects.clear()
	}

	private fun releaseRecord()
	{
		record?.let {
			try { it.stop() } catch(e: Exception) { }
			it.release()
		}
		record = null
		releaseEffects()
	}
}
