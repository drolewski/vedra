package io.vedra.infrastructure.slack.service

import io.vedra.infrastructure.slack.formatter.FeedbackBlockBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlackMessageFormatterTest {
    private val formatter = SlackMessageFormatter()

    @Test
    fun `buildConfirmationBlocks preserves promote button`() {
        val promoteButton =
            mapOf(
                "type" to "button",
                "text" to mapOf("type" to "plain_text", "text" to FeedbackBlockBuilder.LABEL_SHARE),
                "action_id" to "promote_public",
                "value" to "fb-1",
            )
        val original =
            listOf(
                mapOf("type" to "section", "text" to mapOf("type" to "mrkdwn", "text" to "answer")),
                mapOf(
                    "type" to "actions",
                    "block_id" to FeedbackBlockBuilder.BLOCK_ACTIONS,
                    "elements" to
                        listOf(
                            mapOf(
                                "type" to "button",
                                "text" to mapOf("type" to "plain_text", "text" to FeedbackBlockBuilder.LABEL_HELPFUL),
                                "action_id" to "feedback_positive",
                                "value" to "fb-1",
                            ),
                            mapOf(
                                "type" to "button",
                                "text" to mapOf("type" to "plain_text", "text" to FeedbackBlockBuilder.LABEL_NOT_HELPFUL),
                                "action_id" to "feedback_negative",
                                "value" to "fb-1",
                            ),
                            promoteButton,
                        ),
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val result = formatter.buildConfirmationBlocks(original, "positive") as List<Map<String, Any>>

        val actionsBlock = result.firstOrNull { it["block_id"] == FeedbackBlockBuilder.BLOCK_ACTIONS }
        assertTrue(actionsBlock != null, "promote actions block should be kept")
        val elements = actionsBlock["elements"] as List<Map<String, Any>>
        assertEquals(1, elements.size)
        assertEquals("promote_public", elements.first()["action_id"])
    }

    @Test
    fun `buildConfirmationBlocks without promote button behaves as before`() {
        val original =
            listOf(
                mapOf("type" to "section", "text" to mapOf("type" to "mrkdwn", "text" to "answer")),
                mapOf(
                    "type" to "actions",
                    "block_id" to FeedbackBlockBuilder.BLOCK_ACTIONS,
                    "elements" to emptyList<Map<String, Any>>(),
                ),
            )

        @Suppress("UNCHECKED_CAST")
        val result = formatter.buildConfirmationBlocks(original, "positive") as List<Map<String, Any>>

        assertTrue(result.none { it["block_id"] == FeedbackBlockBuilder.BLOCK_ACTIONS })
        assertTrue(result.any { it["type"] == "context" })
    }
}
