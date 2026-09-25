package io.github.yamakenji.archknowledge.core.schema

import io.github.yamakenji.archknowledge.core.graph.ConceptGraph
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.RelationId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId

data class ConceptTypeDefinition(
    val id: ConceptTypeId,
    val description: String,
)

data class EndpointRule(
    val sourceType: ConceptTypeId,
    val targetType: ConceptTypeId,
)

/**
 * @property meaning how to read a stored `source -> target` relation of this type.
 * @property endpoints permitted concept-type pairs in stored direction.
 */
data class RelationTypeDefinition(
    val id: RelationTypeId,
    val meaning: String,
    val endpoints: Set<EndpointRule>,
)

/** Concept and relation types with permitted endpoints, supplied by a Profile. */
class Schema(
    conceptTypes: List<ConceptTypeDefinition>,
    relationTypes: List<RelationTypeDefinition>,
) {
    val conceptTypes: Map<ConceptTypeId, ConceptTypeDefinition> = conceptTypes.associateBy { it.id }
    val relationTypes: Map<RelationTypeId, RelationTypeDefinition> = relationTypes.associateBy { it.id }

    init {
        require(this.conceptTypes.size == conceptTypes.size) { "Duplicate concept type IDs" }
        require(this.relationTypes.size == relationTypes.size) { "Duplicate relation type IDs" }
        relationTypes.forEach { relationType ->
            require(relationType.endpoints.isNotEmpty()) { "Relation type ${relationType.id} has no endpoints" }
            relationType.endpoints.forEach { rule ->
                require(rule.sourceType in this.conceptTypes && rule.targetType in this.conceptTypes) {
                    "Relation type ${relationType.id} uses undefined endpoint types $rule"
                }
            }
        }
    }

    /**
     * Returns all conformance violations of [graph]: concept violations, then relation violations,
     * each ordered by ID. Empty means valid.
     */
    fun validate(graph: ConceptGraph): List<SchemaViolation> {
        val conceptViolations = graph.concepts()
            .filter { it.type !in conceptTypes }
            .map { SchemaViolation.UnknownConceptType(it.id, it.type) }

        val relationViolations = graph.relations().mapNotNull { relation ->
            val definition = relationTypes[relation.type]
                ?: return@mapNotNull SchemaViolation.UnknownRelationType(relation.id, relation.type)
            val sourceType = graph.concept(relation.source)!!.type
            val targetType = graph.concept(relation.target)!!.type
            if (EndpointRule(sourceType, targetType) in definition.endpoints) {
                null
            } else {
                SchemaViolation.DisallowedEndpoints(relation.id, relation.type, sourceType, targetType)
            }
        }
        return conceptViolations + relationViolations
    }
}

sealed interface SchemaViolation {
    val message: String

    data class UnknownConceptType(val concept: ConceptId, val type: ConceptTypeId) : SchemaViolation {
        override val message get() = "Concept $concept has undefined type $type"
    }

    data class UnknownRelationType(val relation: RelationId, val type: RelationTypeId) : SchemaViolation {
        override val message get() = "Relation $relation has undefined type $type"
    }

    data class DisallowedEndpoints(
        val relation: RelationId,
        val type: RelationTypeId,
        val sourceType: ConceptTypeId,
        val targetType: ConceptTypeId,
    ) : SchemaViolation {
        override val message get() = "Relation $relation of type $type does not permit $sourceType -> $targetType"
    }
}
