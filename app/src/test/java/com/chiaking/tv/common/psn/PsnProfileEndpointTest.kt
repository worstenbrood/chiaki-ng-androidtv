package com.chiaking.tv.common.psn

import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PsnProfileEndpointTest {
    @Test fun acceptsSonyProfileService() {
        assertTrue(PsnProfileEndpoint.profileUrl(
            "https://us-prof.np.community.playstation.net/userProfile/"
        ).contains("/userProfile/v1/users/me/profile?"))
    }

    @Test fun rejectsUntrustedDiscoveryDestinations() {
        listOf(
            "http://us-prof.np.community.playstation.net/userProfile",
            "https://us-prof.np.community.playstation.net.evil.test/userProfile",
            "https://evil.test/userProfile",
            "https://name@us-prof.np.community.playstation.net/userProfile",
            "https://us-prof.np.community.playstation.net:8443/userProfile",
            "https://us-prof.np.community.playstation.net/userProfile?next=evil",
            "https://us-prof.np.community.playstation.net/other",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { PsnProfileEndpoint.profileUrl(url) }
        }
    }
}
