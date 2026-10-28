package com.chiaking.tv.common.psn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PsnLoginAttemptTest {
    private val attempt = PsnLoginAttempt("test device", "nonce")

    @Test fun loginUrlCarriesOneDeviceIdentityAndState() {
        assertTrue(attempt.loginUrl.contains("duid=test+device"))
        assertTrue(attempt.loginUrl.contains("state=nonce"))
        assertNotEquals(PsnLoginAttempt("uid").state, PsnLoginAttempt("uid").state)
    }

    @Test fun parsesEncodedRedirectOnlyForThisAttemptAndExactEndpoint() {
        assertEquals("a+b=c", attempt.phoneCode("${PsnAuth.REDIRECT_PAGE}?code=a%2Bb%3Dc&state=nonce"))
        for(url in listOf(
            "${PsnAuth.REDIRECT_PAGE}?code=abcd", "${PsnAuth.REDIRECT_PAGE}?code=abcd&state=wrong",
            "${PsnAuth.REDIRECT_PAGE}?code=abcd&state=nonce&code=other",
            "${PsnAuth.REDIRECT_PAGE}?code=abcd&state=nonce&error=denied",
            "${PsnAuth.REDIRECT_PAGE}/other?code=abcd&state=nonce",
            "https://remoteplay.dl.playstation.net.evil.test/remoteplay/redirect?code=abcd&state=nonce",
            "http://remoteplay.dl.playstation.net/remoteplay/redirect?code=abcd&state=nonce",
            "${PsnAuth.REDIRECT_PAGE}?code=abcd&state=%XX", "abcd"
        )) assertNull(url, attempt.phoneCode(url))
    }

    @Test fun manualFallbackStillSupportsBareCodesAndDecodesFullRedirects() {
        assertEquals("abcd-1234", PsnAuth.parseRedirect("abcd-1234"))
        assertEquals("a+b", PsnAuth.parseRedirect("${PsnAuth.REDIRECT_PAGE}?code=a%2Bb"))
        assertNull(PsnAuth.parseRedirect("${PsnAuth.REDIRECT_PAGE}.evil?code=abcd"))
    }
}
