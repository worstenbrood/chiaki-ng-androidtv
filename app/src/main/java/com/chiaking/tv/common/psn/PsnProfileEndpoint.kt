// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.chiaking.tv.common.psn

import java.net.URI
import java.net.URLEncoder

internal object PsnProfileEndpoint {
    private const val PROFILE_QUERY = "?fields=avatarUrl,personalDetail,plus&avatarUrlScheme=secure"
    /** Check discovery before forwarding the account's bearer token. */
    fun profileUrl(baseUrl: String, onlineId: String): String {
        val uri = URI(baseUrl)
        require(uri.scheme == "https" && uri.rawUserInfo == null)
        require(uri.host?.lowercase()?.endsWith(".np.community.playstation.net") == true)
        require(uri.port == -1 || uri.port == 443)
        require(uri.rawQuery == null && uri.rawFragment == null)
        require(uri.rawPath.trimEnd('/') == "/userProfile")
        require(onlineId.isNotBlank())
        val encoded = URLEncoder.encode(onlineId, "UTF-8").replace("+", "%20")
        return baseUrl.trimEnd('/') + "/v1/users/$encoded/profile" + PROFILE_QUERY
    }
}
