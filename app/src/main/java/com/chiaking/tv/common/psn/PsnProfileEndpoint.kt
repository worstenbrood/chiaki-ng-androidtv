// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.common.psn

import java.net.URI

/** Validate Sony's discovery response before sending a bearer token to it. */
internal object PsnProfileEndpoint {
    fun profileUrl(baseUrl: String): String {
        val uri = URI(baseUrl)
        val host = uri.host?.lowercase() ?: error("Missing profile host")
        require(uri.scheme == "https" && uri.rawUserInfo == null)
        require(host.endsWith(".np.community.playstation.net"))
        require(uri.port == -1 || uri.port == 443)
        require(uri.rawQuery == null && uri.rawFragment == null)
        require(uri.path.trimEnd('/') == "/userProfile")
        // Each request uses that account's own token; 'me' avoids online-ID lookups.
        return baseUrl.trimEnd('/') +
            "/v1/users/me/profile?fields=onlineId,avatarUrl&avatarUrlScheme=secure"
    }
}
