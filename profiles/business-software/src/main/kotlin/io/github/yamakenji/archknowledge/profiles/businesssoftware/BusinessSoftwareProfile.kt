package io.github.yamakenji.archknowledge.profiles.businesssoftware

import io.github.yamakenji.archknowledge.core.analysis.TraversalDirection
import io.github.yamakenji.archknowledge.core.analysis.TraversalPolicy
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId
import io.github.yamakenji.archknowledge.core.schema.ConceptTypeDefinition
import io.github.yamakenji.archknowledge.core.schema.EndpointRule
import io.github.yamakenji.archknowledge.core.schema.RelationTypeDefinition
import io.github.yamakenji.archknowledge.core.schema.Schema

/**
 * Connects Business Architecture to Software Architecture:
 *
 * ```
 * Capability -REALIZED_BY-> BusinessProcess -SUPPORTED_BY-> Application -IMPLEMENTS-> BoundedContext
 * ```
 *
 * Relations are stored in the arrow direction. All relations are many-to-many.
 */
object BusinessSoftwareProfile {
    val CAPABILITY = ConceptTypeId("Capability")
    val BUSINESS_PROCESS = ConceptTypeId("BusinessProcess")
    val APPLICATION = ConceptTypeId("Application")
    val BOUNDED_CONTEXT = ConceptTypeId("BoundedContext")

    val REALIZED_BY = RelationTypeId("REALIZED_BY")
    val SUPPORTED_BY = RelationTypeId("SUPPORTED_BY")
    val IMPLEMENTS = RelationTypeId("IMPLEMENTS")

    val schema = Schema(
        conceptTypes = listOf(
            ConceptTypeDefinition(CAPABILITY, "What the enterprise is able to do"),
            ConceptTypeDefinition(BUSINESS_PROCESS, "A flow of business work that realizes capabilities"),
            ConceptTypeDefinition(APPLICATION, "A software application that supports business processes"),
            ConceptTypeDefinition(
                BOUNDED_CONTEXT,
                "A design boundary within which a domain model is consistent; not a deployment unit",
            ),
        ),
        relationTypes = listOf(
            RelationTypeDefinition(
                REALIZED_BY,
                "The source Capability is realized by the target BusinessProcess",
                setOf(EndpointRule(CAPABILITY, BUSINESS_PROCESS)),
            ),
            RelationTypeDefinition(
                SUPPORTED_BY,
                "The source BusinessProcess is supported by the target Application",
                setOf(EndpointRule(BUSINESS_PROCESS, APPLICATION)),
            ),
            RelationTypeDefinition(
                IMPLEMENTS,
                "The source Application implements (part of) the target BoundedContext",
                setOf(EndpointRule(APPLICATION, BOUNDED_CONTEXT)),
            ),
        ),
    )

    /**
     * "If this concept changes, what depends on it downstream?" Follows the three relations in stored
     * direction; depth 3 covers the full Capability-to-BoundedContext chain.
     */
    val downstreamImpact = TraversalPolicy(
        relationTypes = setOf(REALIZED_BY, SUPPORTED_BY, IMPLEMENTS),
        direction = TraversalDirection.OUTGOING,
        maxDepth = 3,
    )
}
