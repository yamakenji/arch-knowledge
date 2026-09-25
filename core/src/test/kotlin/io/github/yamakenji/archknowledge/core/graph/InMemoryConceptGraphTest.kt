package io.github.yamakenji.archknowledge.core.graph

import io.github.yamakenji.archknowledge.core.node
import io.github.yamakenji.archknowledge.core.rel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemoryConceptGraphTest {
    @Test
    fun `rejects duplicate concept IDs`() {
        assertFailsWith<IllegalArgumentException> { InMemoryConceptGraph(listOf(node("a"), node("a")), emptyList()) }
    }

    @Test
    fun `rejects duplicate relation IDs`() {
        assertFailsWith<IllegalArgumentException> {
            InMemoryConceptGraph(listOf(node("a"), node("b")), listOf(rel("r1:a->b"), rel("r1:b->a")))
        }
    }

    @Test
    fun `rejects relations with missing endpoints`() {
        assertFailsWith<IllegalArgumentException> { InMemoryConceptGraph(listOf(node("a")), listOf(rel("r1:a->b"))) }
    }

    @Test
    fun `lists are ordered by ID`() {
        val graph = InMemoryConceptGraph(
            listOf(node("c"), node("a"), node("b")),
            listOf(rel("r2:a->c"), rel("r1:a->b")),
        )

        assertEquals(listOf("a", "b", "c"), graph.concepts().map { it.id.value })
        assertEquals(listOf("r1", "r2"), graph.outgoing(graph.concepts().first().id).map { it.id.value })
    }
}
