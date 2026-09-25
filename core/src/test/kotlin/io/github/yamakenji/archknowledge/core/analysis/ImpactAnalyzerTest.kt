package io.github.yamakenji.archknowledge.core.analysis

import io.github.yamakenji.archknowledge.core.OTHER
import io.github.yamakenji.archknowledge.core.OTHER_NODE
import io.github.yamakenji.archknowledge.core.graphOf
import io.github.yamakenji.archknowledge.core.node
import io.github.yamakenji.archknowledge.core.policy
import io.github.yamakenji.archknowledge.core.rel
import io.github.yamakenji.archknowledge.core.graph.ConceptGraph
import io.github.yamakenji.archknowledge.core.graph.InMemoryConceptGraph
import io.github.yamakenji.archknowledge.core.model.ConceptId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ImpactAnalyzerTest {

    private fun analyze(graph: ConceptGraph, start: String, policy: TraversalPolicy = policy()): ImpactAnalysis {
        val outcome = ImpactAnalyzer.analyze(graph, ImpactAnalysisRequest(ConceptId(start), policy))
        return assertIs<ImpactAnalysisOutcome.Analyzed>(outcome).analysis
    }

    private fun ImpactAnalysis.ids() = affected.map { it.concept.id.value }

    private fun ImpactAnalysis.paths(id: String) =
        affected.single { it.concept.id.value == id }.evidence.map { path -> path.relationIds.map { it.value } }

    private val chain = graphOf(listOf(rel("r1:a->b"), rel("r2:b->c"), rel("r3:c->d")))

    @Test
    fun `follows a chain and excludes the start concept`() {
        val result = analyze(chain, "a")

        assertEquals(listOf("b", "c", "d"), result.ids())
        assertEquals(listOf(1, 2, 3), result.affected.map { it.distance })
        assertEquals(listOf(listOf("r1", "r2", "r3")), result.paths("d"))
        assertEquals("a", result.start.id.value)
        assertFalse(result.truncated)
    }

    @Test
    fun `branching and converging paths yield one concept with every distinct path`() {
        val diamond = graphOf(listOf(rel("r1:a->b"), rel("r2:a->c"), rel("r3:b->d"), rel("r4:c->d")))

        val result = analyze(diamond, "a")

        assertEquals(listOf("b", "c", "d"), result.ids())
        assertEquals(listOf(listOf("r1", "r3"), listOf("r2", "r4")), result.paths("d"))
    }

    @Test
    fun `distance is the shortest path while longer paths remain as evidence`() {
        val graph = graphOf(listOf(rel("r1:a->b"), rel("r2:a->c"), rel("r3:c->b")))

        val b = analyze(graph, "a").affected.single { it.concept.id.value == "b" }

        assertEquals(1, b.distance)
        assertEquals(listOf(listOf("r1"), listOf("r2", "r3")), b.evidence.map { p -> p.relationIds.map { it.value } })
    }

    @Test
    fun `parallel relations between the same concepts are separate evidence`() {
        val graph = graphOf(listOf(rel("r2:a->b"), rel("r1:a->b")))

        assertEquals(listOf(listOf("r1"), listOf("r2")), analyze(graph, "a").paths("b"))
    }

    @Test
    fun `cycle terminates, never revisits the start, and is not truncation`() {
        val cycle = graphOf(listOf(rel("r1:a->b"), rel("r2:b->c"), rel("r3:c->a")))

        val result = analyze(cycle, "a", policy(maxDepth = 100))

        assertEquals(listOf("b", "c"), result.ids())
        assertEquals(listOf(listOf("r1", "r2")), result.paths("c"))
        assertFalse(result.truncated)
    }

    @Test
    fun `cycle blocked exactly at the depth limit is not truncation`() {
        val twoCycle = graphOf(listOf(rel("r1:a->b"), rel("r2:b->a")))

        val result = analyze(twoCycle, "a", policy(maxDepth = 1))

        assertEquals(listOf("b"), result.ids())
        assertFalse(result.truncated)
    }

    @Test
    fun `self-loop is ignored`() {
        val graph = graphOf(listOf(rel("r1:a->a"), rel("r2:a->b"), rel("r3:b->b")))

        val result = analyze(graph, "a", policy(direction = TraversalDirection.BOTH))

        assertEquals(listOf("b"), result.ids())
        assertEquals(listOf(listOf("r2")), result.paths("b"))
    }

    @Test
    fun `disconnected concepts are excluded`() {
        val graph = graphOf(listOf(rel("r1:a->b"), rel("r9:x->y")), isolated = listOf("z"))

        assertEquals(listOf("b"), analyze(graph, "a").ids())
    }

    @Test
    fun `isolated start yields an empty, complete result`() {
        val graph = graphOf(listOf(rel("r1:a->b")), isolated = listOf("z"))

        val result = analyze(graph, "z")

        assertTrue(result.affected.isEmpty())
        assertFalse(result.truncated)
    }

    @Test
    fun `unknown start concept is reported explicitly`() {
        val outcome = ImpactAnalyzer.analyze(chain, ImpactAnalysisRequest(ConceptId("missing"), policy()))

        assertEquals(ImpactAnalysisOutcome.StartConceptNotFound(ConceptId("missing")), outcome)
    }

    @Test
    fun `depth zero follows nothing and is truncated when relations exist`() {
        val result = analyze(chain, "a", policy(maxDepth = 0))

        assertTrue(result.affected.isEmpty())
        assertTrue(result.truncated)
    }

    @Test
    fun `depth limit below the chain length is truncated`() {
        val result = analyze(chain, "a", policy(maxDepth = 2))

        assertEquals(listOf("b", "c"), result.ids())
        assertTrue(result.truncated)
    }

    @Test
    fun `depth limit equal to or beyond the chain length is complete`() {
        for (depth in listOf(3, 4)) {
            val result = analyze(chain, "a", policy(maxDepth = depth))
            assertEquals(listOf("b", "c", "d"), result.ids(), "maxDepth=$depth")
            assertFalse(result.truncated, "maxDepth=$depth")
        }
    }

    @Test
    fun `truncation loses evidence paths even when every concept was found`() {
        // d is found at depth 1, but the longer path a->b->c->d is cut off at depth 2.
        val graph = graphOf(listOf(rel("r1:a->d"), rel("r2:a->b"), rel("r3:b->c"), rel("r4:c->d")))

        val result = analyze(graph, "a", policy(maxDepth = 2))

        assertEquals(listOf("b", "d", "c"), result.ids())
        assertEquals(listOf(listOf("r1")), result.paths("d"))
        assertTrue(result.truncated)
    }

    @Test
    fun `incoming direction follows relations against stored direction`() {
        val result = analyze(chain, "d", policy(direction = TraversalDirection.INCOMING))

        assertEquals(listOf("c", "b", "a"), result.ids())
        val step = result.affected.single { it.concept.id.value == "c" }.evidence.single().steps.single()
        assertEquals(ConceptId("d"), step.from)
        assertEquals(ConceptId("c"), step.to)
        assertFalse(step.alongStoredDirection)
    }

    @Test
    fun `outgoing direction does not follow incoming relations`() {
        assertTrue(analyze(chain, "d").affected.isEmpty())
    }

    @Test
    fun `both directions reach upstream and downstream`() {
        val result = analyze(chain, "b", policy(direction = TraversalDirection.BOTH))

        assertEquals(listOf("a", "c", "d"), result.ids())
    }

    @Test
    fun `only allowed relation types are followed`() {
        val graph = graphOf(listOf(rel("r1:a->b"), rel("r2:a->c", type = OTHER), rel("r3:b->d", type = OTHER)))

        assertEquals(listOf("b"), analyze(graph, "a").ids())
    }

    @Test
    fun `ordering is by distance, then type, then ordinal ID`() {
        val graph = InMemoryConceptGraph(
            listOf("a", "a10", "a2", "b1").map { node(it) } + node("a0", OTHER_NODE),
            listOf(rel("r1:a->a2"), rel("r2:a->a10"), rel("r3:a->a0"), rel("r4:a2->b1")),
        )

        // "Node" < "OtherNode"; "a10" < "a2" in ordinal string order.
        assertEquals(listOf("a10", "a2", "a0", "b1"), analyze(graph, "a").ids())
    }

    @Test
    fun `result does not depend on input order`() {
        val relations = listOf(rel("r1:a->b"), rel("r2:a->c"), rel("r3:b->d"), rel("r4:c->d"), rel("r5:d->a"))
        val baseline = analyze(graphOf(relations), "a")

        repeat(5) { seed ->
            val shuffled = graphOf(relations.shuffled(kotlin.random.Random(seed)))
            assertEquals(baseline, analyze(shuffled, "a"))
        }
    }

    @Test
    fun `evidence paths start at the start concept, end at the affected concept, and use existing relations`() {
        val graph = graphOf(listOf(rel("r1:a->b"), rel("r2:a->c"), rel("r3:b->d"), rel("r4:c->d"), rel("r5:d->b")))
        val known = graph.relations().toSet()

        val result = analyze(graph, "a", policy(direction = TraversalDirection.BOTH))

        for (affected in result.affected) {
            for (path in affected.evidence) {
                assertEquals(ConceptId("a"), path.start)
                assertEquals(affected.concept.id, path.end)
                assertTrue(path.steps.all { it.relation in known })
                val visited = listOf(path.start) + path.steps.map { it.to }
                assertEquals(visited.size, visited.toSet().size, "path revisits a concept: ${path.relationIds}")
            }
            assertEquals(affected.evidence.size, affected.evidence.map { it.relationIds }.toSet().size)
        }
    }

    @Test
    fun `both directions with converging paths yield every simple path`() {
        val graph = graphOf(listOf(rel("r1:a->b"), rel("r2:a->c"), rel("r3:b->d"), rel("r4:c->d"), rel("r5:d->b")))

        val result = analyze(graph, "a", policy(maxDepth = 3, direction = TraversalDirection.BOTH))

        assertEquals(listOf("b", "c", "d"), result.ids())
        assertEquals(listOf(listOf("r1"), listOf("r2", "r4", "r3"), listOf("r2", "r4", "r5")), result.paths("b"))
        assertEquals(listOf(listOf("r2"), listOf("r1", "r3", "r4"), listOf("r1", "r5", "r4")), result.paths("c"))
        assertEquals(listOf(listOf("r1", "r3"), listOf("r1", "r5"), listOf("r2", "r4")), result.paths("d"))
        assertFalse(result.truncated)
    }

    @Test
    fun `both directions with converging paths truncated one step before the longest path`() {
        val graph = graphOf(listOf(rel("r1:a->b"), rel("r2:a->c"), rel("r3:b->d"), rel("r4:c->d"), rel("r5:d->b")))

        val result = analyze(graph, "a", policy(maxDepth = 2, direction = TraversalDirection.BOTH))

        assertEquals(listOf("b", "c", "d"), result.ids())
        assertEquals(listOf(listOf("r1")), result.paths("b"))
        assertEquals(listOf(listOf("r2")), result.paths("c"))
        assertEquals(listOf(listOf("r1", "r3"), listOf("r1", "r5"), listOf("r2", "r4")), result.paths("d"))
        assertTrue(result.truncated)
    }

    @Test
    fun `invalid policy is rejected before lookup`() {
        val outcome = ImpactAnalyzer.analyze(
            chain,
            ImpactAnalysisRequest(ConceptId("missing"), policy(maxDepth = -1, relationTypes = emptySet())),
        )

        assertEquals(2, assertIs<ImpactAnalysisOutcome.InvalidRequest>(outcome).problems.size)
    }
}
