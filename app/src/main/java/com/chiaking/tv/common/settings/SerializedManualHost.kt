// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.settings

import com.chiaking.tv.common.MacAddress

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class SerializedManualHost(
	@Json(name = "host") val host: String,
	@Json(name = "server_mac") val serverMac: MacAddress?
)
