package io.github.yamakenji.archknowledge.core.analysis

import io.github.yamakenji.archknowledge.core.graph.ConceptGraph
import io.github.yamakenji.archknowledge.core.model.ConceptId

/**
 * Generic impact traversal. Semantics (see docs/adr/0002):
 *
 * - The start concept is reported separately and never appears in `affected`.
 * - Every simple path (no concept repeated) of at most `maxDepth` relations is evidence.
 *   Paths are identified by their relation-ID sequence, so each is reported once.
 * - Cycles and self-loops end a path; they do not mark the result truncated.
 * - `truncated` is set only when a path reaches `maxDepth` and could still be extended.
 * - Concepts are ordered by (distance, type ID, concept ID); paths by (length, relation IDs).
 *
 * Enumerating all simple paths is exponential in the worst case; the depth limit bounds it.
 * This is acceptable for small, curated models.
 */
object ImpactAnalyzer {
    fun analyze(graph: ConceptGraph, request: ImpactAnalysisRequest): ImpactAnalysisOutcome {
        val problems = validate(request.policy)
        if (problems.isNotEmpty()) return ImpactAnalysisOutcome.InvalidRequest(problems)
        val start = graph.concept(request.start)
            ?: return ImpactAnalysisOutcome.StartConceptNotFound(request.start)

        val paths = mutableListOf<EvidencePath>()
        var truncated = false

        fun explore(current: ConceptId, onPath: Set<ConceptId>, steps: List<TraversalStep>) {
            val next = candidateSteps(graph, current, request.policy).filter { it.to !in onPath }
            if (next.isEmpty()) return
            if (steps.size == request.policy.maxDepth) {
                truncated = true
                return
            }
            for (step in next) {
                val extended = steps + step
                paths += EvidencePath(extended)
                explore(step.to, onPath + step.to, extended)
            }
        }
        explore(start.id, setOf(start.id), emptyList())

        val affected = paths
            .groupBy { it.end }
            .map { (id, evidence) ->
                val ordered = evidence.sortedWith(pathOrder)
                AffectedConcept(graph.concept(id)!!, ordered.first().length, ordered)
            }
            .sortedWith(compareBy({ it.distance }, { it.concept.type.value }, { it.concept.id.value }))

        return ImpactAnalysisOutcome.Analyzed(ImpactAnalysis(start, request.policy, affected, truncated))
    }

    private fun validate(policy: TraversalPolicy): List<String> = buildList {
        if (policy.relationTypes.isEmpty()) add("At least one relation type must be allowed")
        if (policy.maxDepth < 0) add("maxDepth must be >= 0, was ${policy.maxDepth}")
    }

    /** Followable steps from [current], at most one per relation, ordered by relation ID. */
    private fun candidateSteps(graph: ConceptGraph, current: ConceptId, policy: TraversalPolicy): List<TraversalStep> {
        val forward = if (policy.direction != TraversalDirection.INCOMING) {
            graph.outgoing(current).map { TraversalStep(it, current, it.target) }
        } else {
            emptyList()
        }
        val backward = if (policy.direction != TraversalDirection.OUTGOING) {
            graph.incoming(current).map { TraversalStep(it, current, it.source) }
        } else {
            emptyList()
        }
        return (forward + backward)
            .filter { it.relation.type in policy.relationTypes }
            .distinctBy { it.relation.id }
            .sortedBy { it.relation.id.value }
    }

    private val pathOrder: Comparator<EvidencePath> =
        compareBy<EvidencePath> { it.length }.thenComparator { a, b ->
            a.relationIds.zip(b.relationIds)
                .map { (x, y) -> x.value.compareTo(y.value) }
                .firstOrNull { it != 0 } ?: 0
        }
}
