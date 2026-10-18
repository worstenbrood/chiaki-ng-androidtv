// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.session

import com.chiaking.tv.common.settings.Preferences
import com.metallic.chiaki.lib.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Live mic pitch-shift state (Sonic pitch shifter in the native layer).
 * Persisted to [Preferences]; [applyTo] pushes the current setting to a session
 * and every mutation pushes it to the live session immediately, so the HUD chip
 * and the settings screen stay in sync with what the console hears.
 */
class MicPitch(private val preferences: Preferences)
{
	companion object
	{
		const val MIN_SEMITONES = -12
		const val MAX_SEMITONES = 12

		/** Equal temperament: one semitone is the twelfth root of two. */
		fun factorFor(semitones: Int): Float = 2f.pow(semitones / 12f)
	}

	private val _enabled = MutableStateFlow(preferences.micPitchEnabled)
	val enabled: StateFlow<Boolean> get() = _enabled.asStateFlow()

	private val _semitones = MutableStateFlow(preferences.micPitch)
	val semitones: StateFlow<Int> get() = _semitones.asStateFlow()

	/** The live session mutations are pushed to; set on (re)start, cleared on shutdown. */
	@Volatile var session: Session? = null

	/** Bind a (re)started session and push the current setting onto it. */
	fun applyTo(session: Session?)
	{
		this.session = session
		session?.setMicPitch(_enabled.value, factorFor(_semitones.value))
	}

	fun setEnabled(enabled: Boolean)
	{
		_enabled.value = enabled
		preferences.micPitchEnabled = enabled
		session?.setMicPitch(enabled, factorFor(_semitones.value))
	}

	fun setSemitones(semitones: Int)
	{
		val v = semitones.coerceIn(MIN_SEMITONES, MAX_SEMITONES)
		_semitones.value = v
		preferences.micPitch = v
		session?.setMicPitch(_enabled.value, factorFor(v))
	}

	fun toggle() = setEnabled(!_enabled.value)

	fun step(dir: Int) = setSemitones(_semitones.value + dir)
}
