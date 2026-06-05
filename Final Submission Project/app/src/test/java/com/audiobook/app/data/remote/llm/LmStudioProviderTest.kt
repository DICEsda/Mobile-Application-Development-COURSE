package com.audiobook.app.data.remote.llm

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the pure base-URL normalization used by [LmStudioProvider].
 * Written alongside the implementation — the LM Studio host is user-entered, so
 * tolerant normalization matters and is the cheapest seam to lock down.
 */
class LmStudioProviderTest {

    @Test
    fun `bare host gets http scheme and trailing slash`() {
        assertEquals("http://192.168.1.50:1234/", LmStudioProvider.normalizeBaseUrl("192.168.1.50:1234"))
    }

    @Test
    fun `existing scheme is preserved`() {
        assertEquals("https://my-host:1234/", LmStudioProvider.normalizeBaseUrl("https://my-host:1234"))
    }

    @Test
    fun `trailing slash is not duplicated`() {
        assertEquals("http://localhost:1234/", LmStudioProvider.normalizeBaseUrl("http://localhost:1234/"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("http://10.0.2.2:1234/", LmStudioProvider.normalizeBaseUrl("  10.0.2.2:1234  "))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", LmStudioProvider.normalizeBaseUrl("   "))
    }
}
