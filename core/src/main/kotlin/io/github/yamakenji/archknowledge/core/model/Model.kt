package io.github.yamakenji.archknowledge.core.model

/** Stable identifier of a concept instance, independent of its display name. */
@JvmInline
value class ConceptId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConceptId must not be blank" }
    }

    override fun toString(): String = value
}

/** Stable identifier of a relation instance. */
@JvmInline
value class RelationId(val value: String) {
    init {
        require(value.isNotBlank()) { "RelationId must not be blank" }
    }

    override fun toString(): String = value
}

/** Identifier of a concept type defined by a Profile. Core never interprets its value. */
@JvmInline
value class ConceptTypeId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConceptTypeId must not be blank" }
    }

    override fun toString(): String = value
}

/** Identifier of a relation type defined by a Profile. Core never interprets its value. */
@JvmInline
value class RelationTypeId(val value: String) {
    init {
        require(value.isNotBlank()) { "RelationTypeId must not be blank" }
    }

    override fun toString(): String = value
}

data class Concept(
    val id: ConceptId,
    val type: ConceptTypeId,
    val name: String,
    val description: String? = null,
    val provenance: String? = null,
)

/**
 * A directed, typed relation stored as `source -> target`.
 * The meaning of the stored direction is defined by the relation type in a Profile.
 */
data class Relation(
    val id: RelationId,
    val type: RelationTypeId,
    val source: ConceptId,
    val target: ConceptId,
    val provenance: String? = null,
)
