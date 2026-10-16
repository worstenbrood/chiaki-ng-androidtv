// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.db

import androidx.room.Embedded

data class ManualHostAndRegisteredHost(
	@Embedded(prefix = "manual_host_") val manualHost: ManualHost,
	@Embedded val registeredHost: RegisteredHost?
)
