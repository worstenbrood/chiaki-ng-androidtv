// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.ui

/**
 * Process-wide store of the last focused item index per menu, so re-entering a
 * screen (or reopening the HUD) restores the cursor where the user left it.
 */
object FocusMemory
{
	private val map = mutableMapOf<String, Int>()

	fun get(key: String, default: Int = 0): Int = map[key] ?: default

	fun set(key: String, index: Int)
	{
		map[key] = index
	}
}
