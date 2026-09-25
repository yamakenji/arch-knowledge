package io.github.yamakenji.archknowledge.core.analysis

import io.github.yamakenji.archknowledge.core.model.Concept
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.Relation
import io.github.yamakenji.archknowledge.core.model.RelationId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId

/** Which stored direction of a relation may be followed from the current concept. */
enum class TraversalDirection {
    /** Follow `source -> target`. */
    OUTGOING,

    /** Follow `target -> source`. */
    INCOMING,

    /** Follow either direction. */
    BOTH,
}

/**
 * @property relationTypes only relations of these types are followed.
 * @property direction applied to every relation type in [relationTypes].
 * @property maxDepth maximum number of relations in an evidence path; 0 follows nothing.
 */
data class TraversalPolicy(
    val relationTypes: Set<RelationTypeId>,
    val direction: TraversalDirection,
    val maxDepth: Int,
)

data class ImpactAnalysisRequest(
    val start: ConceptId,
    val policy: TraversalPolicy,
)

/** One followed relation. [from] and [to] give the traversal order, which may oppose the stored direction. */
data class TraversalStep(
    val relation: Relation,
    val from: ConceptId,
    val to: ConceptId,
) {
    val alongStoredDirection: Boolean get() = relation.source == from
}

/** A simple path (no repeated concept) of followed relations beginning at the start concept. */
data class EvidencePath(val steps: List<TraversalStep>) {
    init {
        require(steps.isNotEmpty()) { "An evidence path has at least one step" }
        require(steps.zipWithNext().all { (a, b) -> a.to == b.from }) { "Evidence path steps must be connected" }
    }

    val length: Int get() = steps.size
    val start: ConceptId get() = steps.first().from
    val end: ConceptId get() = steps.last().to
    val relationIds: List<RelationId> get() = steps.map { it.relation.id }
}

/**
 * @property distance length of the shortest evidence path.
 * @property evidence every distinct evidence path to [concept] within the depth limit, ordered.
 */
data class AffectedConcept(
    val concept: Concept,
    val distance: Int,
    val evidence: List<EvidencePath>,
)

/**
 * @property affected concepts reachable from [start], excluding [start] itself, ordered.
 * @property truncated true when the depth limit stopped traversal while followable relations remained,
 *   so more affected concepts or evidence paths may exist.
 */
data class ImpactAnalysis(
    val start: Concept,
    val policy: TraversalPolicy,
    val affected: List<AffectedConcept>,
    val truncated: Boolean,
)

sealed interface ImpactAnalysisOutcome {
    data class Analyzed(val analysis: ImpactAnalysis) : ImpactAnalysisOutcome

    data class StartConceptNotFound(val start: ConceptId) : ImpactAnalysisOutcome

    data class InvalidRequest(val problems: List<String>) : ImpactAnalysisOutcome
}
