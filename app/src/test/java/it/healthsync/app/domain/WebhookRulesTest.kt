package it.healthsync.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookRulesTest {
    @Test
    fun emptyHeaderNameBecomesAuthorization() {
        assertEquals(DEFAULT_AUTH_HEADER, normalizeHeaderName("  "))
        assertEquals("X-Api-Key", normalizeHeaderName(" X-Api-Key "))
    }

    @Test
    fun acceptsHttpsAndRejectsBadUrls() {
        val ok = validateWebhookSettings(" https://hooks.example/salute ", "Authorization", "secret")
        assertTrue(ok.ok)
        assertNull(validateWebhookSettings("http://192.168.1.20:8080/hook", "Authorization", "k").urlError)
        assertTrue(validateWebhookSettings("", "Authorization", "k").urlError!!.contains("URL"))
        assertTrue(validateWebhookSettings("ftp://example.com", "Authorization", "k").urlError!!.contains("https"))
        assertTrue(validateWebhookSettings("https://", "Authorization", "k").urlError != null)
    }

    @Test
    fun rejectsHeaderInjectionAndBlankKey() {
        val badName = validateWebhookSettings("https://example.com", "Auth orization", "k")
        assertFalse(badName.ok)
        val injected = validateWebhookSettings("https://example.com", "Authorization", "secret\r\nX-Evil: 1")
        assertTrue(injected.headerValueError!!.contains("capo"))
        assertTrue(validateWebhookSettings("https://example.com", "Authorization", "  ").headerValueError != null)
    }

    @Test
    fun retryableStatusesAreServerAndRateLimit() {
        assertTrue(isRetryableHttpStatus(500))
        assertTrue(isRetryableHttpStatus(503))
        assertTrue(isRetryableHttpStatus(429))
        assertTrue(isRetryableHttpStatus(408))
        assertFalse(isRetryableHttpStatus(200))
        assertFalse(isRetryableHttpStatus(401))
        assertFalse(isRetryableHttpStatus(404))
    }
}
