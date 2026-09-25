package io.github.yamakenji.archknowledge.core.graph

import io.github.yamakenji.archknowledge.core.model.Concept
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.Relation

/**
 * Read-only access to concepts and relations.
 *
 * Implementations guarantee unique concept and relation IDs, that every relation endpoint exists,
 * and that all lists are ordered by ID (ordinal string comparison).
 */
interface ConceptGraph {
    fun concept(id: ConceptId): Concept?

    fun concepts(): List<Concept>

    fun relations(): List<Relation>

    /** Relations stored with [id] as source. */
    fun outgoing(id: ConceptId): List<Relation>

    /** Relations stored with [id] as target. */
    fun incoming(id: ConceptId): List<Relation>
}

class InMemoryConceptGraph(concepts: Collection<Concept>, relations: Collection<Relation>) : ConceptGraph {
    private val conceptsById: Map<ConceptId, Concept>
    private val orderedRelations: List<Relation>
    private val outgoingById: Map<ConceptId, List<Relation>>
    private val incomingById: Map<ConceptId, List<Relation>>

    init {
        duplicates(concepts.map { it.id.value }).let {
            require(it.isEmpty()) { "Duplicate concept IDs: $it" }
        }
        duplicates(relations.map { it.id.value }).let {
            require(it.isEmpty()) { "Duplicate relation IDs: $it" }
        }
        conceptsById = concepts.sortedBy { it.id.value }.associateBy { it.id }
        val dangling = relations
            .filter { it.source !in conceptsById || it.target !in conceptsById }
            .map { it.id.value }
            .sorted()
        require(dangling.isEmpty()) { "Relations with missing endpoint concepts: $dangling" }

        orderedRelations = relations.sortedBy { it.id.value }
        outgoingById = orderedRelations.groupBy { it.source }
        incomingById = orderedRelations.groupBy { it.target }
    }

    override fun concept(id: ConceptId): Concept? = conceptsById[id]

    override fun concepts(): List<Concept> = conceptsById.values.toList()

    override fun relations(): List<Relation> = orderedRelations

    override fun outgoing(id: ConceptId): List<Relation> = outgoingById[id].orEmpty()

    override fun incoming(id: ConceptId): List<Relation> = incomingById[id].orEmpty()

    private fun duplicates(ids: List<String>): List<String> =
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
}
