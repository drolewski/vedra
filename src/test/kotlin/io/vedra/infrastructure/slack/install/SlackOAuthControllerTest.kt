package io.vedra.infrastructure.slack.install

import io.mockk.mockk
import io.vedra.infrastructure.slack.SlackOAuthConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlackOAuthControllerTest {
    @Test
    fun `result page escapes untrusted error and contact values`() {
        val controller =
            SlackOAuthController(
                mockk(),
                SlackOAuthConfig("", "", "", ""),
                "<img src=x onerror=alert(1)>",
            )
        val body = controller.success("<script>alert(1)</script>").body!!
        assertFalse(body.contains("<script>"))
        assertFalse(body.contains("<img"))
        assertTrue(body.contains("&lt;script&gt;"))
        assertTrue(body.contains("&lt;img"))
    }
}
