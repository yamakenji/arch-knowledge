package io.github.yamakenji.archknowledge.core.schema

import io.github.yamakenji.archknowledge.core.LINK
import io.github.yamakenji.archknowledge.core.NODE
import io.github.yamakenji.archknowledge.core.OTHER
import io.github.yamakenji.archknowledge.core.OTHER_NODE
import io.github.yamakenji.archknowledge.core.node
import io.github.yamakenji.archknowledge.core.rel
import io.github.yamakenji.archknowledge.core.graph.InMemoryConceptGraph
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.RelationId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaTest {
    private val schema = Schema(
        listOf(ConceptTypeDefinition(NODE, "node"), ConceptTypeDefinition(OTHER_NODE, "other")),
        listOf(RelationTypeDefinition(LINK, "source links to target", setOf(EndpointRule(NODE, OTHER_NODE)))),
    )

    @Test
    fun `conforming graph has no violations`() {
        val graph = InMemoryConceptGraph(listOf(node("a"), node("b", OTHER_NODE)), listOf(rel("r1:a->b")))

        assertTrue(schema.validate(graph).isEmpty())
    }

    @Test
    fun `reports undefined types and disallowed endpoints`() {
        val graph = InMemoryConceptGraph(
            listOf(node("a"), node("b", OTHER_NODE), node("x", ConceptTypeId("Unknown"))),
            listOf(rel("r1:b->a"), rel("r2:a->b", type = OTHER)),
        )

        assertEquals(
            listOf(
                SchemaViolation.UnknownConceptType(ConceptId("x"), ConceptTypeId("Unknown")),
                SchemaViolation.DisallowedEndpoints(RelationId("r1"), LINK, OTHER_NODE, NODE),
                SchemaViolation.UnknownRelationType(RelationId("r2"), OTHER),
            ),
            schema.validate(graph),
        )
    }

    @Test
    fun `violation order does not depend on input order`() {
        val concepts = listOf(
            node("a"),
            node("b", OTHER_NODE),
            node("x2", ConceptTypeId("Unknown")),
            node("x1", ConceptTypeId("Unknown")),
        )
        val relations = listOf(rel("r3:b->a"), rel("r1:a->b", type = OTHER), rel("r2:b->a"), rel("r4:a->b"))
        val baseline = schema.validate(InMemoryConceptGraph(concepts, relations))

        assertEquals(
            listOf(
                SchemaViolation.UnknownConceptType(ConceptId("x1"), ConceptTypeId("Unknown")),
                SchemaViolation.UnknownConceptType(ConceptId("x2"), ConceptTypeId("Unknown")),
                SchemaViolation.UnknownRelationType(RelationId("r1"), OTHER),
                SchemaViolation.DisallowedEndpoints(RelationId("r2"), LINK, OTHER_NODE, NODE),
                SchemaViolation.DisallowedEndpoints(RelationId("r3"), LINK, OTHER_NODE, NODE),
            ),
            baseline,
        )
        repeat(5) { seed ->
            val random = kotlin.random.Random(seed)
            val shuffled = InMemoryConceptGraph(concepts.shuffled(random), relations.shuffled(random))
            assertEquals(baseline, schema.validate(shuffled), "seed=$seed")
        }
    }

    @Test
    fun `schema rejects endpoints with undefined concept types`() {
        assertFailsWith<IllegalArgumentException> {
            Schema(
                listOf(ConceptTypeDefinition(NODE, "node")),
                listOf(RelationTypeDefinition(RelationTypeId("R"), "r", setOf(EndpointRule(NODE, OTHER_NODE)))),
            )
        }
    }
}
