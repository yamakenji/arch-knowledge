package io.github.yamakenji.archknowledge.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** End-to-end acceptance through the MVP entry point. */
class CliTest {
    private data class Result(val exitCode: Int, val out: String, val err: String)

    private fun cli(vararg args: String): Result {
        val out = StringBuilder()
        val err = StringBuilder()
        val code = run(args.toList(), out, err)
        return Result(code, out.toString(), err.toString())
    }

    @Test
    fun `impact report for the order management capability`() {
        val result = cli("impact", "cap-order-management")

        assertEquals(EXIT_OK, result.exitCode)
        assertEquals("", result.err)
        assertEquals(
            """
            Start: cap-order-management [Capability] 受注管理
            Traversal: OUTGOING via IMPLEMENTS, REALIZED_BY, SUPPORTED_BY; max depth 3
            Result: complete within the registered relations

            Affected concepts (8):
              1. bp-order-fulfillment [BusinessProcess] 出荷手配  distance 1
                   cap-order-management -[REALIZED_BY rel-002]-> bp-order-fulfillment
              2. bp-order-intake [BusinessProcess] 注文受付  distance 1
                   cap-order-management -[REALIZED_BY rel-001]-> bp-order-intake
              3. app-crm [Application] Customer Relationship Management  distance 2
                   cap-order-management -[REALIZED_BY rel-001]-> bp-order-intake -[SUPPORTED_BY rel-004]-> app-crm
              4. app-oms [Application] Order Management System  distance 2
                   cap-order-management -[REALIZED_BY rel-001]-> bp-order-intake -[SUPPORTED_BY rel-003]-> app-oms
                   cap-order-management -[REALIZED_BY rel-002]-> bp-order-fulfillment -[SUPPORTED_BY rel-005]-> app-oms
              5. app-wms [Application] Warehouse Management System  distance 2
                   cap-order-management -[REALIZED_BY rel-002]-> bp-order-fulfillment -[SUPPORTED_BY rel-006]-> app-wms
              6. bc-customer [BoundedContext] Customer Context  distance 3
                   cap-order-management -[REALIZED_BY rel-001]-> bp-order-intake -[SUPPORTED_BY rel-004]-> app-crm -[IMPLEMENTS rel-008]-> bc-customer
              7. bc-order [BoundedContext] Order Context  distance 3
                   cap-order-management -[REALIZED_BY rel-001]-> bp-order-intake -[SUPPORTED_BY rel-003]-> app-oms -[IMPLEMENTS rel-007]-> bc-order
                   cap-order-management -[REALIZED_BY rel-002]-> bp-order-fulfillment -[SUPPORTED_BY rel-005]-> app-oms -[IMPLEMENTS rel-007]-> bc-order
              8. bc-shipping [BoundedContext] Shipping Context  distance 3
                   cap-order-management -[REALIZED_BY rel-002]-> bp-order-fulfillment -[SUPPORTED_BY rel-006]-> app-wms -[IMPLEMENTS rel-009]-> bc-shipping

            Note: potential dependency impact derived from registered relations only;
                  it does not confirm actual business consequences.

            """.trimIndent(),
            result.out,
        )
    }

    @Test
    fun `depth limit is reported as incomplete`() {
        val result = cli("impact", "cap-order-management", "--depth", "1")

        assertEquals(EXIT_OK, result.exitCode)
        assertTrue(result.out.contains("Result: INCOMPLETE - depth limit 1 reached"))
        assertTrue(result.out.contains("Affected concepts (2):"))
    }

    @Test
    fun `concept without downstream relations does not claim no impact`() {
        val result = cli("impact", "bc-order")

        assertEquals(EXIT_OK, result.exitCode)
        assertTrue(result.out.contains("No affected concepts found through registered relations."))
        assertTrue(result.out.contains("does not show that there is no impact"))
    }

    @Test
    fun `unknown start concept fails with guidance`() {
        val result = cli("impact", "cap-unknown")

        assertEquals(EXIT_FAILURE, result.exitCode)
        assertEquals("", result.out)
        assertTrue(result.err.contains("Concept not found: cap-unknown"))
    }

    @Test
    fun `negative depth is an invalid request`() {
        val result = cli("impact", "cap-order-management", "--depth", "-1")

        assertEquals(EXIT_FAILURE, result.exitCode)
        assertTrue(result.err.contains("maxDepth must be >= 0"))
    }

    @Test
    fun `malformed arguments print usage`() {
        val malformed = listOf(
            emptyList(),
            listOf("impact"),
            listOf("impact", "x", "--depth", "two"),
            listOf("impact", "--bogus"),
            listOf("impact", "--bogus", "--depth", "2"),
            listOf("impact", "--depth", "2"),
            listOf("nope"),
        )
        for (args in malformed) {
            val result = cli(*args.toTypedArray())
            assertEquals(EXIT_USAGE, result.exitCode, "args=$args")
            assertTrue(result.err.startsWith("Usage:"), "args=$args")
        }
    }

    @Test
    fun `concepts lists every example concept`() {
        val result = cli("concepts")

        assertEquals(EXIT_OK, result.exitCode)
        assertEquals(13, result.out.lines().filter { it.isNotBlank() }.size)
        assertTrue(result.out.contains("cap-order-management  [Capability]  受注管理"))
    }
}
