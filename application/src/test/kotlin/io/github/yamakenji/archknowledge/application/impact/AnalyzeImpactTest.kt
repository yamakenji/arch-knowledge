package io.github.yamakenji.archknowledge.application.impact

import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisOutcome
import io.github.yamakenji.archknowledge.core.analysis.TraversalDirection
import io.github.yamakenji.archknowledge.core.analysis.TraversalPolicy
import io.github.yamakenji.archknowledge.core.graph.InMemoryConceptGraph
import io.github.yamakenji.archknowledge.core.model.Concept
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.Relation
import io.github.yamakenji.archknowledge.core.model.RelationId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId
import io.github.yamakenji.archknowledge.core.schema.ConceptTypeDefinition
import io.github.yamakenji.archknowledge.core.schema.EndpointRule
import io.github.yamakenji.archknowledge.core.schema.RelationTypeDefinition
import io.github.yamakenji.archknowledge.core.schema.Schema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AnalyzeImpactTest {
    private val node = ConceptTypeId("Node")
    private val link = RelationTypeId("LINK")
    private val schema = Schema(
        listOf(ConceptTypeDefinition(node, "node")),
        listOf(RelationTypeDefinition(link, "links to", setOf(EndpointRule(node, node)))),
    )
    private val graph = InMemoryConceptGraph(
        listOf("a", "b", "c").map { Concept(ConceptId(it), node, it) },
        listOf(
            Relation(RelationId("r1"), link, ConceptId("a"), ConceptId("b")),
            Relation(RelationId("r2"), link, ConceptId("b"), ConceptId("c")),
        ),
    )
    private val defaultPolicy = TraversalPolicy(setOf(link), TraversalDirection.OUTGOING, maxDepth = 5)
    private val useCase = AnalyzeImpact(graph, schema, defaultPolicy)

    @Test
    fun `applies the default policy`() {
        val analysis = assertIs<ImpactAnalysisOutcome.Analyzed>(useCase.execute(ImpactQuery("a"))).analysis

        assertEquals(defaultPolicy, analysis.policy)
        assertEquals(listOf("b", "c"), analysis.affected.map { it.concept.id.value })
    }

    @Test
    fun `query options override the default policy`() {
        val outcome = useCase.execute(ImpactQuery("c", maxDepth = 1, direction = TraversalDirection.INCOMING))
        val analysis = assertIs<ImpactAnalysisOutcome.Analyzed>(outcome).analysis

        assertEquals(TraversalPolicy(setOf(link), TraversalDirection.INCOMING, 1), analysis.policy)
        assertEquals(listOf("b"), analysis.affected.map { it.concept.id.value })
        assertEquals(true, analysis.truncated)
    }

    @Test
    fun `unknown relation types are invalid`() {
        val outcome = useCase.execute(ImpactQuery("a", relationTypes = setOf("LINK", "NOPE")))

        assertEquals(ImpactAnalysisOutcome.InvalidRequest(listOf("Unknown relation types: NOPE")), outcome)
    }

    @Test
    fun `blank relation types are rejected rather than dropped`() {
        for (types in listOf(setOf("LINK", " "), setOf(""))) {
            assertEquals(
                ImpactAnalysisOutcome.InvalidRequest(listOf("Relation types must not be blank")),
                useCase.execute(ImpactQuery("a", relationTypes = types)),
                "relationTypes=$types",
            )
        }
    }

    @Test
    fun `empty relation types are invalid`() {
        assertEquals(
            ImpactAnalysisOutcome.InvalidRequest(listOf("At least one relation type must be allowed")),
            useCase.execute(ImpactQuery("a", relationTypes = emptySet())),
        )
    }

    @Test
    fun `blank start ID is invalid`() {
        assertIs<ImpactAnalysisOutcome.InvalidRequest>(useCase.execute(ImpactQuery(" ")))
    }

    @Test
    fun `negative depth is invalid`() {
        assertIs<ImpactAnalysisOutcome.InvalidRequest>(useCase.execute(ImpactQuery("a", maxDepth = -1)))
    }

    @Test
    fun `unknown start ID is not found`() {
        assertEquals(
            ImpactAnalysisOutcome.StartConceptNotFound(ConceptId("zzz")),
            useCase.execute(ImpactQuery("zzz")),
        )
    }
}
