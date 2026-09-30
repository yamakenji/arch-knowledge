package io.github.yamakenji.archknowledge.adapters.neo4j

import io.github.yamakenji.archknowledge.core.graph.InMemoryConceptGraph
import io.github.yamakenji.archknowledge.core.model.Concept
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.Relation
import io.github.yamakenji.archknowledge.core.model.RelationId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId
import io.github.yamakenji.archknowledge.core.schema.Schema
import org.neo4j.driver.Driver
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.Value
import org.neo4j.driver.exceptions.Neo4jException

sealed interface SnapshotOutcome<out T> {
    data class Success<T>(val value: T) : SnapshotOutcome<T>
    data class InvalidData(val problems: List<String>) : SnapshotOutcome<Nothing>
    data class InfrastructureFailure(val message: String) : SnapshotOutcome<Nothing>
}

/** Whole-model replacement in a dedicated database. The caller owns and closes the driver. */
class Neo4jSnapshotStore(private val driver: Driver, private val database: String = "neo4j") {
    fun save(concepts: Collection<Concept>, relations: Collection<Relation>, schema: Schema): SnapshotOutcome<Unit> {
        val validated = validate(concepts, relations, schema)
        val graph = when (validated) {
            is SnapshotOutcome.Success -> validated.value
            is SnapshotOutcome.InvalidData -> return validated
            is SnapshotOutcome.InfrastructureFailure -> return validated
        }
        return infrastructure {
            driver.session(SessionConfig.forDatabase(database)).use { session ->
                session.executeWrite { tx ->
                    tx.run("MATCH ()-[r:RELATION]->() DELETE r").consume()
                    tx.run("MATCH (n:Concept) DETACH DELETE n").consume()
                    tx.run(
                        "UNWIND \$concepts AS c CREATE (n:Concept) SET n = c",
                        mapOf("concepts" to graph.concepts().map {
                            mapOf("id" to it.id.value, "type" to it.type.value, "name" to it.name,
                                "description" to it.description, "provenance" to it.provenance)
                        }),
                    ).consume()
                    tx.run(
                        """UNWIND ${'$'}relations AS r
                           MATCH (s:Concept {id: r.source}), (t:Concept {id: r.target})
                           CREATE (s)-[e:RELATION]->(t)
                           SET e.id = r.id, e.type = r.type, e.provenance = r.provenance""",
                        mapOf("relations" to graph.relations().map {
                            mapOf("id" to it.id.value, "type" to it.type.value, "source" to it.source.value,
                                "target" to it.target.value, "provenance" to it.provenance)
                        }),
                    ).consume()
                }
            }
            SnapshotOutcome.Success(Unit)
        }
    }

    fun load(schema: Schema): SnapshotOutcome<InMemoryConceptGraph> = infrastructure {
        driver.session(SessionConfig.forDatabase(database)).use { session ->
            session.executeRead { tx ->
                val record = tx.run(
                    """CALL { MATCH (n:Concept) RETURN collect(properties(n)) AS concepts }
                       CALL { MATCH (s)-[r:RELATION]->(t)
                              RETURN collect({properties: properties(r), source: s.id, target: t.id,
                                  validEndpoints: s:Concept AND t:Concept}) AS relations }
                       RETURN concepts, relations""",
                ).single()
                try {
                    val concepts = record["concepts"].asList { c ->
                        Concept(ConceptId(c.required("id")), ConceptTypeId(c.required("type")),
                            c.required("name"), c.optional("description"), c.optional("provenance"))
                    }
                    val relations = record["relations"].asList { r ->
                        require(r["validEndpoints"].asBoolean()) { "RELATION endpoint is not a Concept" }
                        val p = r["properties"]
                        Relation(RelationId(p.required("id")), RelationTypeId(p.required("type")),
                            ConceptId(r.required("source")), ConceptId(r.required("target")), p.optional("provenance"))
                    }
                    validate(concepts, relations, schema)
                } catch (_: IllegalArgumentException) {
                    SnapshotOutcome.InvalidData(listOf("Stored snapshot contains malformed properties or endpoints"))
                } catch (_: org.neo4j.driver.exceptions.value.ValueException) {
                    SnapshotOutcome.InvalidData(listOf("Stored snapshot contains incorrectly typed properties"))
                }
            }
        }
    }

    private fun validate(concepts: Collection<Concept>, relations: Collection<Relation>, schema: Schema): SnapshotOutcome<InMemoryConceptGraph> {
        val graph = try {
            InMemoryConceptGraph(concepts, relations)
        } catch (e: IllegalArgumentException) {
            return SnapshotOutcome.InvalidData(listOf(e.message ?: "Invalid graph structure"))
        }
        val problems = schema.validate(graph).map { it.message }
        return if (problems.isEmpty()) SnapshotOutcome.Success(graph) else SnapshotOutcome.InvalidData(problems)
    }

    private fun Value.required(key: String): String {
        val value = this[key]
        require(value.type().name() == "STRING") { "Missing or non-string property" }
        return value.asString()
    }

    private fun Value.optional(key: String): String? = if (this[key].isNull) null else required(key)

    private fun <T> infrastructure(block: () -> SnapshotOutcome<T>): SnapshotOutcome<T> = try {
        block()
    } catch (_: Neo4jException) {
        SnapshotOutcome.InfrastructureFailure("Neo4j operation failed; check connectivity, authentication and database availability")
    } catch (_: IllegalStateException) {
        SnapshotOutcome.InfrastructureFailure("Neo4j driver is unavailable")
    }
}