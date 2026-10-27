package com.chiaking.tv.session

/** Public profile data only: never expose PSN tokens to the stream UI. */
data class CoopAccountOption(
    val rid: String,
    val available: Boolean,
    val onlineId: String = "",
    val avatarUrl: String = "",
)

data class CoopAccountRequest(val id: Long, val slot: Int, val accounts: List<CoopAccountOption>)

internal enum class CoopNoticeKind { JOINED, REFUSED, FULL, PASSCODE, PROFILE_REQUIRED, TIMEOUT, PS5_ONLY }
internal data class CoopNotice(val slot: Int, val kind: CoopNoticeKind)
