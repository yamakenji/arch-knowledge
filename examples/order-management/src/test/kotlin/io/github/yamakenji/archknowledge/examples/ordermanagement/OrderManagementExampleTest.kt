package io.github.yamakenji.archknowledge.examples.ordermanagement

import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysis
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisOutcome
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisRequest
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalyzer
import io.github.yamakenji.archknowledge.core.analysis.TraversalPolicy
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Pins the expected results documented in examples/order-management/README.md. */
class OrderManagementExampleTest {
    private val graph = OrderManagementExample.graph()

    private fun analyze(start: String, policy: TraversalPolicy = BusinessSoftwareProfile.downstreamImpact): ImpactAnalysis {
        val outcome = ImpactAnalyzer.analyze(graph, ImpactAnalysisRequest(ConceptId(start), policy))
        return assertIs<ImpactAnalysisOutcome.Analyzed>(outcome).analysis
    }

    private fun ImpactAnalysis.summary() = affected.map { "${it.concept.type}:${it.concept.id}" }

    private fun ImpactAnalysis.paths(id: String) =
        affected.single { it.concept.id.value == id }.evidence.map { p -> p.relationIds.joinToString(",") }

    @Test
    fun `example conforms to the business-software profile`() {
        val violations = BusinessSoftwareProfile.schema.validate(graph)

        assertTrue(violations.isEmpty(), violations.joinToString { it.message })
    }

    @Test
    fun `order management capability reaches its processes, applications, and bounded contexts`() {
        val result = analyze("cap-order-management")

        assertEquals(
            listOf(
                "BusinessProcess:bp-order-fulfillment",
                "BusinessProcess:bp-order-intake",
                "Application:app-crm",
                "Application:app-oms",
                "Application:app-wms",
                "BoundedContext:bc-customer",
                "BoundedContext:bc-order",
                "BoundedContext:bc-shipping",
            ),
            result.summary(),
        )
        assertEquals(listOf("rel-001,rel-003,rel-007", "rel-002,rel-005,rel-007"), result.paths("bc-order"))
        assertEquals(listOf("rel-001,rel-003", "rel-002,rel-005"), result.paths("app-oms"))
        assertFalse(result.truncated)
    }

    @Test
    fun `unrelated HR concepts are excluded`() {
        val ids = analyze("cap-order-management").affected.map { it.concept.id.value }

        assertTrue(ids.none { it in setOf("cap-hr-management", "bp-payroll", "app-hris", "bc-payroll") })
    }

    @Test
    fun `depth two stops at applications and is marked incomplete`() {
        val result = analyze("cap-order-management", BusinessSoftwareProfile.downstreamImpact.copy(maxDepth = 2))

        assertEquals(5, result.affected.size)
        assertTrue(result.affected.none { it.concept.type == BusinessSoftwareProfile.BOUNDED_CONTEXT })
        assertTrue(result.truncated)
    }

    @Test
    fun `starting mid-chain only reaches downstream concepts`() {
        assertEquals(
            listOf(
                "Application:app-oms",
                "Application:app-wms",
                "BoundedContext:bc-order",
                "BoundedContext:bc-shipping",
            ),
            analyze("bp-order-fulfillment").summary(),
        )
    }
}
