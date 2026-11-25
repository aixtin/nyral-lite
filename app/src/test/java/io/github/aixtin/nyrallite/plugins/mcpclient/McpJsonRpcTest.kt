package io.github.aixtin.nyrallite.plugins.mcpclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpJsonRpcTest {

    @Test
    fun parsesPlainJsonBody() {
        val result = McpJsonRpc.resultOf(
            """{"jsonrpc":"2.0","id":3,"result":{"tools":[]}}""",
            3,
        )

        assertTrue(result.has("tools"))
    }

    @Test
    fun parsesSseWithEventLine() {
        val body = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"n\":7}}\n\n"

        assertEquals(7, McpJsonRpc.resultOf(body, 1).optInt("n"))
    }

    @Test
    fun picksMatchingIdFromMultipleEvents() {
        val body = buildString {
            append("event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n")
            append("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{\"ok\":true}}\n\n")
        }

        assertTrue(McpJsonRpc.resultOf(body, 9).optBoolean("ok"))
    }

    @Test(expected = RuntimeException::class)
    fun throwsWhenIdMissing() {
        McpJsonRpc.resultOf("event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}\n\n", 5)
    }
}
