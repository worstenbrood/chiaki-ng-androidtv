// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.chiaking.tv.common.psn

enum class PsnLoginPhase { GENERATING_UID, EXCHANGING, FETCHING_ACCOUNT, SAVING }

class PsnLoginException(val phase: PsnLoginPhase) : Exception("PSN login failed at $phase")
