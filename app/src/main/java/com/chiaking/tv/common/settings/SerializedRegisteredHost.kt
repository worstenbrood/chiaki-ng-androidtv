// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.settings

import com.chiaking.tv.common.db.RegisteredHost

import com.chiaking.tv.common.MacAddress

import com.metallic.chiaki.lib.Target
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class SerializedRegisteredHost(
	@Json(name = "target") val target: Target,
	@Json(name = "ap_ssid") val apSsid: String?,
	@Json(name = "ap_bssid") val apBssid: String?,
	@Json(name = "ap_key") val apKey: String?,
	@Json(name = "ap_name") val apName: String?,
	@Json(name = "server_mac") val serverMac: MacAddress,
	@Json(name = "server_nickname") val serverNickname: String?,
	@Json(name = "rp_regist_key") val rpRegistKey: ByteArray,
	@Json(name = "rp_key_type") val rpKeyType: Int,
	@Json(name = "rp_key") val rpKey: ByteArray
){
	constructor(registeredHost: RegisteredHost) : this(
		registeredHost.target,
		registeredHost.apSsid,
		registeredHost.apBssid,
		registeredHost.apKey,
		registeredHost.apName,
		registeredHost.serverMac,
		registeredHost.serverNickname,
		registeredHost.rpRegistKey,
		registeredHost.rpKeyType,
		registeredHost.rpKey
	)
}
