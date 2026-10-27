package com.chiaking.tv.common.psn

import org.junit.Assert.*
import org.junit.Test

class PsnProfileEndpointTest {
    @Test fun usesOnlineIdAndOriginalRemotePlayFields() {
        assertEquals("https://us-prof.np.community.playstation.net/userProfile/v1/users/Player-1/profile" +
            "?fields=avatarUrl,personalDetail,plus&avatarUrlScheme=secure",
            PsnProfileEndpoint.profileUrl("https://us-prof.np.community.playstation.net/userProfile/", "Player-1"))
    }

    @Test fun encodesOnlineIdAsSinglePathSegment() {
        assertTrue(PsnProfileEndpoint.profileUrl(
            "https://us-prof.np.community.playstation.net/userProfile", "a/b?c d")
            .contains("/users/a%2Fb%3Fc%20d/profile?"))
    }

    @Test fun refusesCredentialForwardingToUntrustedService() {
        listOf("http://us-prof.np.community.playstation.net/userProfile",
            "https://us-prof.np.community.playstation.net.evil.test/userProfile",
            "https://evil.test/userProfile", "https://user@us-prof.np.community.playstation.net/userProfile",
            "https://us-prof.np.community.playstation.net:8443/userProfile",
            "https://us-prof.np.community.playstation.net/userProfile?redirect=evil",
            "https://us-prof.np.community.playstation.net/other").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { PsnProfileEndpoint.profileUrl(url, "Player") }
        }
    }
}
