package io.github.yamakenji.archknowledge.application.impact

import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisOutcome
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisRequest
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalyzer
import io.github.yamakenji.archknowledge.core.analysis.TraversalDirection
import io.github.yamakenji.archknowledge.core.analysis.TraversalPolicy
import io.github.yamakenji.archknowledge.core.graph.ConceptGraph
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId
import io.github.yamakenji.archknowledge.core.schema.Schema

/** Structured impact query. Unset options fall back to the configured default policy. */
data class ImpactQuery(
    val startConceptId: String,
    val maxDepth: Int? = null,
    val direction: TraversalDirection? = null,
    val relationTypes: Set<String>? = null,
)

/**
 * Impact analysis use case. Every entry point (CLI now, natural language later) goes through here.
 *
 * The [graph] is expected to conform to [schema]; validate it when loading.
 */
class AnalyzeImpact(
    private val graph: ConceptGraph,
    private val schema: Schema,
    private val defaultPolicy: TraversalPolicy,
) {
    fun execute(query: ImpactQuery): ImpactAnalysisOutcome {
        if (query.startConceptId.isBlank()) {
            return ImpactAnalysisOutcome.InvalidRequest(listOf("Start concept ID must not be blank"))
        }
        if (query.relationTypes?.any { it.isBlank() } == true) {
            return ImpactAnalysisOutcome.InvalidRequest(listOf("Relation types must not be blank"))
        }
        val relationTypes = query.relationTypes
            ?.map { RelationTypeId(it) }
            ?.toSet()
            ?: defaultPolicy.relationTypes
        val unknown = relationTypes.filter { it !in schema.relationTypes }.map { it.value }.sorted()
        if (unknown.isNotEmpty()) {
            return ImpactAnalysisOutcome.InvalidRequest(listOf("Unknown relation types: ${unknown.joinToString()}"))
        }

        val policy = TraversalPolicy(
            relationTypes = relationTypes,
            direction = query.direction ?: defaultPolicy.direction,
            maxDepth = query.maxDepth ?: defaultPolicy.maxDepth,
        )
        return ImpactAnalyzer.analyze(graph, ImpactAnalysisRequest(ConceptId(query.startConceptId), policy))
    }
}
