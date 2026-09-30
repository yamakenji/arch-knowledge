package io.github.yamakenji.archknowledge.adapters.neo4j

import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisRequest
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalyzer
import io.github.yamakenji.archknowledge.core.graph.InMemoryConceptGraph
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId
import io.github.yamakenji.archknowledge.core.schema.ConceptTypeDefinition
import io.github.yamakenji.archknowledge.core.schema.EndpointRule
import io.github.yamakenji.archknowledge.core.schema.RelationTypeDefinition
import io.github.yamakenji.archknowledge.core.schema.Schema
import io.github.yamakenji.archknowledge.examples.ordermanagement.OrderManagementExample
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import org.neo4j.harness.Neo4jBuilders
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Neo4jSnapshotStoreTest {
    @Test
    fun `round trip validation and failure outcomes against disposable Neo4j`() {
        val directory = Files.createTempDirectory(java.nio.file.Path.of("build"), "neo4j-test-")
        Neo4jBuilders.newInProcessBuilder(directory).withDisabledServer().build().use { neo4j ->
            GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none()).use { driver ->
                val store = Neo4jSnapshotStore(driver)
                val schema = BusinessSoftwareProfile.schema
                val concepts = OrderManagementExample.concepts.mapIndexed { i, c ->
                    c.copy(name = c.name + " ' $ ", description = if (i == 0) "Unicode 日本語 ' quote" else null)
                }
                val relations = OrderManagementExample.relations
                val expected = InMemoryConceptGraph(concepts, relations)
                fun load() = assertIs<SnapshotOutcome.Success<InMemoryConceptGraph>>(store.load(schema)).value
                fun save() = assertIs<SnapshotOutcome.Success<Unit>>(store.save(concepts.reversed(), relations.reversed(), schema))

                assertEquals(emptyList(), load().concepts())
                save()
                save()
                assertEquals(expected.concepts(), load().concepts())
                assertEquals(expected.relations(), load().relations())
                val request = ImpactAnalysisRequest(ConceptId("cap-order-management"), BusinessSoftwareProfile.downstreamImpact)
                assertEquals(ImpactAnalyzer.analyze(expected, request), ImpactAnalyzer.analyze(load(), request))

                val invalidImports = listOf(
                    (concepts + concepts.first()) to relations,
                    concepts to (relations + relations.first()),
                    concepts to listOf(relations.first().copy(target = ConceptId("missing"))),
                    concepts.map { it.copy(type = ConceptTypeId("unknown")) } to relations,
                    concepts to listOf(relations.first().copy(type = RelationTypeId("unknown"))),
                    concepts to listOf(relations.first().copy(target = concepts.first().id)),
                )
                invalidImports.forEach { (c, r) ->
                    assertIs<SnapshotOutcome.InvalidData>(store.save(c, r, schema))
                    assertEquals(expected.concepts(), load().concepts())
                    assertEquals(expected.relations(), load().relations())
                }

                val corruptions = listOf(
                    "MATCH (n:Concept) WHERE n.id = 'cap-order-management' SET n.type = 'unknown'",
                    "MATCH ()-[r:RELATION]->() SET r.type = 'unknown'",
                    "MATCH (n:Concept) WHERE n.id = 'cap-order-management' REMOVE n.id",
                    "MATCH (n:Concept) SET n.name = 42",
                    "MATCH (n:Concept) SET n.provenance = 42",
                    "CREATE (:Concept {id: 'cap-order-management', type: 'Capability', name: 'duplicate'})",
                    "MATCH (s:Concept {id:'cap-order-management'}), (t:Concept {id:'bp-order-intake'}) CREATE (s)-[:RELATION {id:'rel-001',type:'REALIZED_BY'}]->(t)",
                    "MATCH (s:Concept {id:'cap-order-management'}) CREATE (s)-[:RELATION {id:'bad',type:'REALIZED_BY'}]->(:Other {id:'missing'})",
                    "CREATE (:Other {id:'a'})-[:RELATION {id:'bad',type:'REALIZED_BY'}]->(:Other {id:'b'})",
                    "MATCH (s:Concept {id:'cap-order-management'}) CREATE (s)-[:RELATION {id:'bad',type:'REALIZED_BY'}]->(s)",
                )
                corruptions.forEach { query ->
                    save()
                    driver.session().use { session -> session.run(query).consume() }
                    assertIs<SnapshotOutcome.InvalidData>(store.load(schema), query)
                }
                save()
                driver.session().use { session ->
                    session.run("CREATE CONSTRAINT test_unique_name FOR (n:Concept) REQUIRE n.name IS UNIQUE").consume()
                }
                val conflicting = concepts.map { it.copy(name = "same") }
                assertIs<SnapshotOutcome.InfrastructureFailure>(store.save(conflicting, relations, schema))
                assertEquals(expected.concepts(), load().concepts())
                assertEquals(expected.relations(), load().relations())
                driver.session().use { session -> session.run("DROP CONSTRAINT test_unique_name").consume() }
                assertIs<SnapshotOutcome.Success<Unit>>(store.save(emptyList(), emptyList(), schema))
                assertEquals(emptyList(), load().relations())
                assertEquals(emptyList(), load().concepts())

                val node = ConceptTypeId("Node")
                val link = RelationTypeId("LINK")
                val genericSchema = Schema(listOf(ConceptTypeDefinition(node, "Test")),
                    listOf(RelationTypeDefinition(link, "links", setOf(EndpointRule(node, node)))))
                val genericConcepts = concepts.take(2).map { it.copy(type = node, provenance = null) }
                val genericRelations = listOf(relations.first().copy(type = link, source = genericConcepts[0].id,
                    target = genericConcepts[1].id, provenance = null))
                assertIs<SnapshotOutcome.Success<Unit>>(store.save(genericConcepts, genericRelations, genericSchema))
                val generic = assertIs<SnapshotOutcome.Success<InMemoryConceptGraph>>(store.load(genericSchema)).value
                assertEquals(genericConcepts.sortedBy { it.id.value }, generic.concepts())
                assertEquals(genericRelations, generic.relations())
                assertIs<SnapshotOutcome.InfrastructureFailure>(Neo4jSnapshotStore(driver, "nonexistent").load(schema))
            }
            val closed = GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none())
            closed.close()
            assertIs<SnapshotOutcome.InfrastructureFailure>(Neo4jSnapshotStore(closed).load(BusinessSoftwareProfile.schema))
        }
    }
}