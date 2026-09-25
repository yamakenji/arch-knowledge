package io.github.yamakenji.archknowledge.profiles.businesssoftware

import io.github.yamakenji.archknowledge.core.analysis.TraversalDirection
import io.github.yamakenji.archknowledge.core.graph.InMemoryConceptGraph
import io.github.yamakenji.archknowledge.core.model.Concept
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.Relation
import io.github.yamakenji.archknowledge.core.model.RelationId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId
import io.github.yamakenji.archknowledge.core.schema.SchemaViolation
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.APPLICATION
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.BOUNDED_CONTEXT
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.BUSINESS_PROCESS
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.CAPABILITY
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.IMPLEMENTS
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.REALIZED_BY
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.SUPPORTED_BY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BusinessSoftwareProfileTest {
    private val concepts = listOf(
        Concept(ConceptId("cap"), CAPABILITY, "Cap"),
        Concept(ConceptId("bp"), BUSINESS_PROCESS, "Process"),
        Concept(ConceptId("app"), APPLICATION, "App"),
        Concept(ConceptId("bc"), BOUNDED_CONTEXT, "Context"),
    )

    private fun relation(id: String, type: RelationTypeId, source: String, target: String) =
        Relation(RelationId(id), type, ConceptId(source), ConceptId(target))

    private fun violations(vararg relations: Relation) =
        BusinessSoftwareProfile.schema.validate(InMemoryConceptGraph(concepts, relations.toList()))

    @Test
    fun `accepts the full chain in stored direction`() {
        val result = violations(
            relation("r1", REALIZED_BY, "cap", "bp"),
            relation("r2", SUPPORTED_BY, "bp", "app"),
            relation("r3", IMPLEMENTS, "app", "bc"),
        )

        assertTrue(result.isEmpty(), result.toString())
    }

    @Test
    fun `rejects reversed stored direction`() {
        val result = violations(relation("r1", REALIZED_BY, "bp", "cap"))

        assertIs<SchemaViolation.DisallowedEndpoints>(result.single())
    }

    @Test
    fun `rejects relations that skip a layer`() {
        val result = violations(relation("r1", IMPLEMENTS, "cap", "bc"))

        assertIs<SchemaViolation.DisallowedEndpoints>(result.single())
    }

    @Test
    fun `rejects concept types outside the profile`() {
        val graph = InMemoryConceptGraph(concepts + Concept(ConceptId("x"), ConceptTypeId("Server"), "X"), emptyList())

        assertIs<SchemaViolation.UnknownConceptType>(BusinessSoftwareProfile.schema.validate(graph).single())
    }

    @Test
    fun `downstream impact follows the three relations outward over the full chain`() {
        val policy = BusinessSoftwareProfile.downstreamImpact

        assertEquals(setOf(REALIZED_BY, SUPPORTED_BY, IMPLEMENTS), policy.relationTypes)
        assertEquals(TraversalDirection.OUTGOING, policy.direction)
        assertEquals(3, policy.maxDepth)
        assertTrue(policy.relationTypes.all { it in BusinessSoftwareProfile.schema.relationTypes })
    }
}
