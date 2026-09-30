package io.github.yamakenji.archknowledge.cli

import org.neo4j.driver.AuthTokens
import org.neo4j.driver.GraphDatabase
import org.neo4j.harness.Neo4jBuilders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Neo4jCliTest {
    private data class Result(val code: Int, val out: String, val err: String)

    private fun cli(args: List<String>, environment: Map<String, String> = emptyMap()): Result {
        val out = StringBuilder()
        val err = StringBuilder()
        return Result(run(args, out, err, environment), out.toString(), err.toString())
    }

    @Test
    fun `persisted snapshot has identical reports and explicit error outcomes`() {
        Neo4jBuilders.newInProcessBuilder().withDisabledServer().build().use { server ->
            val environment = mapOf("NEO4J_URI" to server.boltURI().toString(),
                "NEO4J_USERNAME" to "neo4j", "NEO4J_PASSWORD" to "test-only-secret")
            assertEquals(EXIT_USAGE, cli(listOf("neo4j", "import-example"), environment).code)
            assertEquals(EXIT_OK, cli(listOf("neo4j", "import-example", "--replace"), environment).code)
            val commands = listOf(listOf("concepts"), listOf("impact", "cap-order-management"),
                listOf("impact", "cap-order-management", "--depth", "0"),
                listOf("impact", "cap-order-management", "--depth", "1"),
                listOf("impact", "cap-order-management", "--depth", "2"),
                listOf("impact", "missing"), listOf("impact", "cap-order-management", "--depth", "-1"))
            for (command in commands) {
                assertEquals(cli(command), cli(listOf("neo4j") + command, environment), command.toString())
            }
            GraphDatabase.driver(server.boltURI(), AuthTokens.none()).use { driver ->
                driver.session().use { it.run("MATCH (n:Concept) SET n.type = 'Unknown'").consume() }
            }
            val invalid = cli(listOf("neo4j", "concepts"), environment)
            assertEquals(EXIT_INVALID_DATA, invalid.code)
            assertEquals("", invalid.out)
            assertTrue(invalid.err.startsWith("Invalid Neo4j snapshot:"))
            val driver = GraphDatabase.driver(server.boltURI(), AuthTokens.none())
            assertEquals(EXIT_INVALID_DATA, run(listOf("neo4j", "concepts"), StringBuilder(), StringBuilder(),
                environment, driverFactory = { _, _, _ -> driver }))
            assertFailsWith<IllegalStateException> { driver.session() }
            val importDriver = GraphDatabase.driver(server.boltURI(), AuthTokens.none())
            assertEquals(EXIT_OK, run(listOf("neo4j", "import-example", "--replace"), StringBuilder(), StringBuilder(),
                environment, driverFactory = { _, _, _ -> importDriver }))
            assertFailsWith<IllegalStateException> { importDriver.session() }
        }
    }

    @Test
    fun `configuration and driver failures redact secrets`() {
        assertEquals(EXIT_USAGE, cli(listOf("neo4j", "concepts")).code)
        assertEquals(EXIT_INFRASTRUCTURE, cli(listOf("neo4j", "concepts"),
            mapOf("NEO4J_URI" to "invalid-scheme://secret", "NEO4J_USERNAME" to "secret-user",
                "NEO4J_PASSWORD" to "secret-password")).code)
        val out = StringBuilder()
        val err = StringBuilder()
        val code = run(listOf("neo4j", "concepts"), out, err,
            mapOf("NEO4J_URI" to "secret-uri", "NEO4J_USERNAME" to "secret-user", "NEO4J_PASSWORD" to "secret-password"),
            driverFactory = { _, _, _ -> error("secret-password secret-uri secret-user") })
        assertEquals(EXIT_INFRASTRUCTURE, code)
        assertFalse(err.contains("secret"))
        assertEquals("", out.toString())
    }
}