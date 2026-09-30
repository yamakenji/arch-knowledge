package io.github.yamakenji.archknowledge.application.impact

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

class AnalyzeNaturalLanguageImpactTest {
    private val seedType = ConceptTypeId("Seed")
    private val otherType = ConceptTypeId("Other")
    private val link = RelationTypeId("LINK")
    private val schema = Schema(
        listOf(ConceptTypeDefinition(seedType, "seed"), ConceptTypeDefinition(otherType, "other")),
        listOf(RelationTypeDefinition(link, "links to", setOf(EndpointRule(seedType, seedType)))),
    )
    private val graph = InMemoryConceptGraph(
        listOf(
            Concept(ConceptId("z"), seedType, "Duplicate"),
            Concept(ConceptId("a"), seedType, "Duplicate"),
            Concept(ConceptId("b"), seedType, "Unique"),
            Concept(ConceptId("c"), seedType, "Tail"),
            Concept(ConceptId("other"), otherType, "Excluded"),
        ),
        listOf(
            Relation(RelationId("r1"), link, ConceptId("a"), ConceptId("b")),
            Relation(RelationId("r2"), link, ConceptId("b"), ConceptId("c")),
        ),
    )

    private fun useCase(
        outcome: ImpactInterpretationOutcome,
        analyze: AnalyzeImpact = AnalyzeImpact(graph, schema, TraversalPolicy(setOf(link), TraversalDirection.OUTGOING, 1)),
    ) = AnalyzeNaturalLanguageImpact(ImpactIntentInterpreter { outcome }, graph, analyze, setOf(seedType))

    private fun interpreted(intent: ImpactIntent) = ImpactInterpretationOutcome.Interpreted(intent)

    @Test
    fun `ID and exact name produce identical complete structured results including evidence and depth`() {
        for (depth in listOf(0, 1, 2)) {
            val analyze = AnalyzeImpact(graph, schema, TraversalPolicy(setOf(link), TraversalDirection.OUTGOING, depth))
            for ((intent, id) in listOf(
                ImpactIntent("impact", startConceptId = "a") to "a",
                ImpactIntent("impact", startConceptName = "Unique") to "b",
            )) {
                val result = assertIs<NaturalLanguageImpactOutcome.Analyzed>(
                    useCase(interpreted(intent), analyze).execute("potential impact?"),
                )
                assertEquals(analyze.execute(ImpactQuery(id)), result.result)
            }
        }
    }

    @Test
    fun `ambiguous exact names return sorted choices and never guess`() {
        val useCase = useCase(interpreted(ImpactIntent("impact", startConceptName = "Duplicate")))
        val result = assertIs<NaturalLanguageImpactOutcome.Ambiguous>(useCase.execute("impact?"))
        assertEquals(listOf("a", "z"), result.choices.map { it.id.value })
        assertEquals(listOf("Duplicate", "Duplicate"), result.choices.map { it.name })
        assertEquals(listOf(seedType, seedType), result.choices.map { it.type })
        val selected = assertIs<NaturalLanguageImpactOutcome.Analyzed>(useCase.execute("impact?", "a"))
        val analyze = AnalyzeImpact(graph, schema, TraversalPolicy(setOf(link), TraversalDirection.OUTGOING, 1))
        assertEquals(analyze.execute(ImpactQuery("a")), selected.result)
        for (selection in listOf("b", "other", "missing", "")) {
            assertEquals(NaturalLanguageImpactOutcome.InvalidSelection, useCase.execute("impact?", selection))
        }
    }

    @Test
    fun `selection cannot override unique ID or bypass intent validation`() {
        assertEquals(
            NaturalLanguageImpactOutcome.InvalidSelection,
            useCase(interpreted(ImpactIntent("impact", startConceptId = "a"))).execute("impact?", "b"),
        )
        val cases = listOf(
            ImpactIntent("delete", startConceptId = "a") to NaturalLanguageImpactOutcome.UnsupportedRequest,
            ImpactIntent("impact") to NaturalLanguageImpactOutcome.MalformedInterpretation,
            ImpactIntent("impact", startConceptId = "missing") to NaturalLanguageImpactOutcome.UnknownConcept,
            ImpactIntent("impact", startConceptId = "other") to NaturalLanguageImpactOutcome.SeedTypeNotPermitted,
        )
        for ((intent, expected) in cases) {
            assertEquals(expected, useCase(interpreted(intent)).execute("impact?", "a"))
        }
    }

    @Test
    fun `missing conflicting and blank fields are malformed`() {
        for (intent in listOf(
            ImpactIntent(null, startConceptId = "a"),
            ImpactIntent(" ", startConceptId = "a"),
            ImpactIntent("impact"),
            ImpactIntent("impact", "a", "Unique"),
            ImpactIntent("impact", startConceptId = " "),
            ImpactIntent("impact", startConceptName = ""),
        )) {
            assertEquals(NaturalLanguageImpactOutcome.MalformedInterpretation, useCase(interpreted(intent)).execute("impact?"))
        }
    }

    @Test
    fun `resolution is exact without case folding fuzzy matching or ID fallback`() {
        for (intent in listOf(
            ImpactIntent("impact", startConceptId = "missing"),
            ImpactIntent("impact", startConceptName = "unique"),
            ImpactIntent("impact", startConceptName = " Unique "),
            ImpactIntent("impact", startConceptName = "b"),
        )) {
            assertEquals(NaturalLanguageImpactOutcome.UnknownConcept, useCase(interpreted(intent)).execute("impact?"))
        }
        assertEquals(
            NaturalLanguageImpactOutcome.SeedTypeNotPermitted,
            useCase(interpreted(ImpactIntent("impact", startConceptName = "Excluded"))).execute("impact?"),
        )
    }

    @Test
    fun `port failures are explicit and exceptions are redacted`() {
        val cases = listOf(
            ImpactInterpretationOutcome.Malformed to NaturalLanguageImpactOutcome.MalformedInterpretation,
            ImpactInterpretationOutcome.Unsupported to NaturalLanguageImpactOutcome.UnsupportedRequest,
            ImpactInterpretationOutcome.InfrastructureFailure to NaturalLanguageImpactOutcome.InterpretationFailure,
        )
        for ((interpretation, expected) in cases) {
            assertEquals(expected, useCase(interpretation).execute("impact?", "a"))
        }
        val throwing = AnalyzeNaturalLanguageImpact(
            ImpactIntentInterpreter { error("secret provider payload") }, graph,
            AnalyzeImpact(graph, schema, TraversalPolicy(setOf(link), TraversalDirection.OUTGOING, 1)), setOf(seedType),
        )
        assertEquals(NaturalLanguageImpactOutcome.InterpretationFailure, throwing.execute("impact?"))
        assertEquals(NaturalLanguageImpactOutcome.InvalidRequest, throwing.execute(" ", "a"))
    }

    @Test
    fun `application retains analysis validation failures`() {
        val invalid = AnalyzeImpact(graph, schema, TraversalPolicy(setOf(link), TraversalDirection.OUTGOING, -1))
        val result = assertIs<NaturalLanguageImpactOutcome.AnalysisRejected>(
            useCase(interpreted(ImpactIntent("impact", startConceptId = "a")), invalid).execute("impact?"),
        )
        assertEquals(invalid.execute(ImpactQuery("a")), result.result)
    }

    @Test
    fun `selection is revalidated when interpretation changes`() {
        var intent = ImpactIntent("impact", startConceptName = "Duplicate")
        val useCase = AnalyzeNaturalLanguageImpact(
            ImpactIntentInterpreter { interpreted(intent) }, graph,
            AnalyzeImpact(graph, schema, TraversalPolicy(setOf(link), TraversalDirection.OUTGOING, 1)), setOf(seedType),
        )
        assertIs<NaturalLanguageImpactOutcome.Ambiguous>(useCase.execute("impact?"))
        intent = ImpactIntent("impact", startConceptName = "Unique")
        assertEquals(NaturalLanguageImpactOutcome.InvalidSelection, useCase.execute("impact?", "a"))
        intent = ImpactIntent("delete", startConceptId = "a")
        assertEquals(NaturalLanguageImpactOutcome.UnsupportedRequest, useCase.execute("impact?", "a"))
    }
}