package com.chiaking.tv.common.db

import com.chiaking.tv.common.MacAddress

/** Import records link by MAC because source database row IDs are not portable. */
data class ManualHostImport(val host: String, val serverMac: MacAddress?)
