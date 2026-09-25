package io.github.yamakenji.archknowledge.core

import io.github.yamakenji.archknowledge.core.analysis.TraversalDirection
import io.github.yamakenji.archknowledge.core.analysis.TraversalPolicy
import io.github.yamakenji.archknowledge.core.graph.InMemoryConceptGraph
import io.github.yamakenji.archknowledge.core.model.Concept
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.Relation
import io.github.yamakenji.archknowledge.core.model.RelationId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId

// Deliberately non-business vocabulary: Core must work with any schema.
val NODE = ConceptTypeId("Node")
val OTHER_NODE = ConceptTypeId("OtherNode")
val LINK = RelationTypeId("LINK")
val OTHER = RelationTypeId("OTHER")

fun node(id: String, type: ConceptTypeId = NODE) = Concept(ConceptId(id), type, id.uppercase())

/** Relation spec "id:source->target", typed [type]. */
fun rel(spec: String, type: RelationTypeId = LINK): Relation {
    val (id, endpoints) = spec.split(":")
    val (source, target) = endpoints.split("->")
    return Relation(RelationId(id), type, ConceptId(source), ConceptId(target))
}

/** Builds a graph whose concepts are every ID mentioned in [relations] plus [isolated]. */
fun graphOf(relations: List<Relation>, isolated: List<String> = emptyList()): InMemoryConceptGraph {
    val ids = (relations.flatMap { listOf(it.source.value, it.target.value) } + isolated).distinct()
    return InMemoryConceptGraph(ids.map { node(it) }, relations)
}

fun policy(
    maxDepth: Int = 10,
    direction: TraversalDirection = TraversalDirection.OUTGOING,
    relationTypes: Set<RelationTypeId> = setOf(LINK),
) = TraversalPolicy(relationTypes, direction, maxDepth)
