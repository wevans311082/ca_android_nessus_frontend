package uk.co.cyberask.scanner

import uk.co.cyberask.scanner.data.NessusApiFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class NessusApiFactoryTest {
    @Test
    fun normalizeBaseUrl_removesTrailingSlashAndAddsOne() {
        val result = NessusApiFactory.normalizeBaseUrl("https://nessus.local:8834/")
        assertEquals("https://nessus.local:8834/", result)
    }

    @Test
    fun normalizeBaseUrl_handlesNoTrailingSlash() {
        val result = NessusApiFactory.normalizeBaseUrl("https://nessus.local:8834")
        assertEquals("https://nessus.local:8834/", result)
    }
}
