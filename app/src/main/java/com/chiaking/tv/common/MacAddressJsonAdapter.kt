// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson

class MacAddressJsonAdapter
{
	@ToJson fun toJson(macAddress: MacAddress) = macAddress.toString()
	@FromJson fun fromJson(string: String) = try { MacAddress(string) } catch(e: IllegalArgumentException) { throw JsonDataException(e.message) }
}
