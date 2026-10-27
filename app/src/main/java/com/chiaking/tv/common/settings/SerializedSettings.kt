// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.settings

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SerializedSettings(
	@Json(name = "registered_hosts") val registeredHosts: List<SerializedRegisteredHost>,
	@Json(name = "manual_hosts") val manualHosts: List<SerializedManualHost>
)
