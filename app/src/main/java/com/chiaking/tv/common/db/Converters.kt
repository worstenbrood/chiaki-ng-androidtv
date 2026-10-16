// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import com.chiaking.tv.common.MacAddress
import androidx.room.TypeConverter
import com.metallic.chiaki.lib.Target

internal class Converters
{
	@TypeConverter
	fun macFromValue(v: Long) = MacAddress(v)

	@TypeConverter
	fun macToValue(addr: MacAddress) = addr.value

	@TypeConverter
	fun targetFromValue(v: Int) = Target.fromValue(v)

	@TypeConverter
	fun targetToValue(target: Target) = target.value
}
