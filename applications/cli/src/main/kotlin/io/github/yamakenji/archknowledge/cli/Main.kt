package io.github.yamakenji.archknowledge.cli

import io.github.yamakenji.archknowledge.application.impact.AnalyzeImpact
import io.github.yamakenji.archknowledge.application.impact.ImpactQuery
import io.github.yamakenji.archknowledge.application.impact.AnalyzeNaturalLanguageImpact
import io.github.yamakenji.archknowledge.application.impact.ImpactIntentInterpreter
import io.github.yamakenji.archknowledge.application.impact.NaturalLanguageImpactOutcome
import io.github.yamakenji.archknowledge.adapters.neo4j.Neo4jSnapshotStore
import io.github.yamakenji.archknowledge.adapters.neo4j.SnapshotOutcome
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisOutcome
import io.github.yamakenji.archknowledge.core.graph.ConceptGraph
import io.github.yamakenji.archknowledge.examples.ordermanagement.OrderManagementExample
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile
import kotlin.system.exitProcess
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

const val EXIT_OK = 0
const val EXIT_FAILURE = 1
const val EXIT_USAGE = 2
const val EXIT_INVALID_DATA = 3
const val EXIT_INFRASTRUCTURE = 4

private val USAGE = """
    Usage:
      arch-knowledge concepts                        List concepts in the Order Management example
      arch-knowledge impact <conceptId> [--depth N]  Show potential downstream impact of a concept
      arch-knowledge neo4j concepts
      arch-knowledge neo4j impact <conceptId> [--depth N]
      arch-knowledge neo4j import-example --replace  Destructively replace the dedicated database snapshot
      arch-knowledge ask "<question>" [--select <conceptId>]
      arch-knowledge neo4j ask "<question>" [--select <conceptId>]
""".trimIndent()

fun main(args: Array<String>) {
    exitProcess(run(args.toList(), System.out, System.err))
}

/** Runs against the bundled example or an explicitly selected Neo4j snapshot; owns created drivers. */
fun run(
    args: List<String>,
    out: Appendable,
    err: Appendable,
    environment: Map<String, String> = System.getenv(),
    driverFactory: (String, String, String) -> Driver = { uri, username, password ->
        GraphDatabase.driver(uri, AuthTokens.basic(username, password))
    },
    interpreterFactory: (Map<String, String>) -> ImpactIntentInterpreter = ::configuredInterpreter,
): Int {
    if (args.firstOrNull() == "neo4j") return runNeo4j(args.drop(1), out, err, environment, driverFactory, interpreterFactory)
    val graph = OrderManagementExample.graph()
    val schema = BusinessSoftwareProfile.schema
    val violations = schema.validate(graph)
    if (violations.isNotEmpty()) {
        err.appendLine("Example model does not conform to its profile:")
        violations.forEach { err.appendLine("  ${it.message}") }
        return EXIT_FAILURE
    }

    return runGraph(args, graph, out, err, environment, interpreterFactory)
}

private fun runGraph(
    args: List<String>, graph: ConceptGraph, out: Appendable, err: Appendable,
    environment: Map<String, String>, interpreterFactory: (Map<String, String>) -> ImpactIntentInterpreter,
): Int {
    val schema = BusinessSoftwareProfile.schema
    return when (args.firstOrNull()) {
        "ask" -> runQuestion(args.drop(1), graph, out, err, environment, interpreterFactory)
        "concepts" -> {
            if (args.size != 1) return usage(err)
            graph.concepts().forEach { out.appendLine("${it.id}  [${it.type}]  ${it.name}") }
            EXIT_OK
        }
        "impact" -> {
            val query = parseImpactQuery(args.drop(1)) ?: return usage(err)
            val useCase = AnalyzeImpact(graph, schema, BusinessSoftwareProfile.downstreamImpact)
            when (val outcome = useCase.execute(query)) {
                is ImpactAnalysisOutcome.Analyzed -> {
                    out.append(ImpactReport.format(outcome.analysis))
                    EXIT_OK
                }
                is ImpactAnalysisOutcome.StartConceptNotFound -> {
                    err.appendLine("Concept not found: ${outcome.start}. Run 'concepts' to list known IDs.")
                    EXIT_FAILURE
                }
                is ImpactAnalysisOutcome.InvalidRequest -> {
                    err.appendLine("Invalid request:")
                    outcome.problems.forEach { err.appendLine("  $it") }
                    EXIT_FAILURE
                }
            }
        }
        else -> usage(err)
    }
}

private fun runNeo4j(
    args: List<String>, out: Appendable, err: Appendable,
    environment: Map<String, String>, driverFactory: (String, String, String) -> Driver,
    interpreterFactory: (Map<String, String>) -> ImpactIntentInterpreter,
): Int {
    val importing = args == listOf("import-example", "--replace")
    if (!importing && args != listOf("concepts") &&
        !(args.firstOrNull() == "impact" && parseImpactQuery(args.drop(1)) != null) &&
        !(args.firstOrNull() == "ask" && validQuestionArgs(args.drop(1)))) return usage(err)
    val keys = listOf("NEO4J_URI", "NEO4J_USERNAME", "NEO4J_PASSWORD")
    if (keys.any { environment[it].isNullOrBlank() } || environment["NEO4J_DATABASE"]?.isBlank() == true) {
        err.appendLine("Neo4j configuration missing: set NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD; NEO4J_DATABASE is optional.")
        return EXIT_USAGE
    }
    return try {
        driverFactory(environment.getValue(keys[0]), environment.getValue(keys[1]), environment.getValue(keys[2])).use { driver ->
            val store = Neo4jSnapshotStore(driver, environment["NEO4J_DATABASE"] ?: "neo4j")
            val outcome = if (importing) {
                val graph = OrderManagementExample.graph()
                store.save(graph.concepts(), graph.relations(), BusinessSoftwareProfile.schema)
            } else store.load(BusinessSoftwareProfile.schema)
            when (outcome) {
                is SnapshotOutcome.Success -> if (importing) {
                    out.appendLine("Order Management example imported; previous snapshot replaced.")
                    EXIT_OK
                } else runGraph(args, outcome.value as ConceptGraph, out, err, environment, interpreterFactory)
                is SnapshotOutcome.InvalidData -> {
                    err.appendLine("Invalid Neo4j snapshot: model structure or profile validation failed.")
                    EXIT_INVALID_DATA
                }
                is SnapshotOutcome.InfrastructureFailure -> {
                    err.appendLine("Neo4j infrastructure failure: check connectivity, authentication and database availability.")
                    EXIT_INFRASTRUCTURE
                }
            }
        }
    } catch (_: Exception) {
        err.appendLine("Neo4j infrastructure failure: check configuration, connectivity, authentication and database availability.")
        EXIT_INFRASTRUCTURE
    }
}

private fun parseImpactQuery(args: List<String>): ImpactQuery? = when {
    args.isEmpty() || args[0].startsWith("--") -> null
    args.size == 1 -> ImpactQuery(args[0])
    args.size == 3 && args[1] == "--depth" -> args[2].toIntOrNull()?.let { ImpactQuery(args[0], maxDepth = it) }
    else -> null
}

private fun usage(err: Appendable): Int {
    err.appendLine(USAGE)
    return EXIT_USAGE
}

private fun validQuestionArgs(args: List<String>): Boolean =
    (args.size == 1 || (args.size == 3 && args[1] == "--select" && args[2].isNotBlank())) &&
        args[0].isNotBlank() && !args[0].startsWith("--")

private fun runQuestion(
    args: List<String>, graph: ConceptGraph, out: Appendable, err: Appendable,
    environment: Map<String, String>, interpreterFactory: (Map<String, String>) -> ImpactIntentInterpreter,
): Int {
    if (!validQuestionArgs(args)) return usage(err)
    val interpreter = try {
        interpreterFactory(environment)
    } catch (_: IllegalArgumentException) {
        err.appendLine("LLM configuration missing or invalid: set LLM_BASE_URL, LLM_MODEL and LLM_API_KEY.")
        return EXIT_USAGE
    } catch (_: Exception) {
        err.appendLine("LLM initialization failed; check provider configuration.")
        return EXIT_INFRASTRUCTURE
    }
    val useCase = AnalyzeNaturalLanguageImpact(
        interpreter, graph,
        AnalyzeImpact(graph, BusinessSoftwareProfile.schema, BusinessSoftwareProfile.downstreamImpact),
        setOf(BusinessSoftwareProfile.CAPABILITY),
    )
    return when (val outcome = useCase.execute(args[0], args.getOrNull(2))) {
        is NaturalLanguageImpactOutcome.Analyzed -> {
            out.append(ImpactReport.format(outcome.result.analysis, includeProvenance = true))
            out.appendLine("Evidence-grounded explanation: these registered dependencies are potential review targets;")
            out.appendLine("actual changes and business consequences require human assessment.")
            EXIT_OK
        }
        is NaturalLanguageImpactOutcome.Ambiguous -> {
            err.appendLine("Ambiguous concept name; rerun the same question with --select <conceptId>:")
            outcome.choices.forEach { err.appendLine("  ${it.id} [${it.type}] ${it.name}") }
            EXIT_FAILURE
        }
        NaturalLanguageImpactOutcome.InterpretationFailure -> {
            err.appendLine("LLM interpretation failed; check provider availability and configuration.")
            EXIT_INFRASTRUCTURE
        }
        else -> {
            val message = when (outcome) {
                NaturalLanguageImpactOutcome.UnknownConcept -> "Concept not found in the registered model."
                NaturalLanguageImpactOutcome.UnsupportedRequest -> "Unsupported request; only Capability dependency impact is permitted."
                NaturalLanguageImpactOutcome.SeedTypeNotPermitted -> "Start concept must be a Capability."
                NaturalLanguageImpactOutcome.InvalidSelection -> "Selected ID does not match this request's candidates."
                NaturalLanguageImpactOutcome.MalformedInterpretation -> "Model response was malformed; no analysis was performed."
                else -> "Invalid impact request; no analysis was performed."
            }
            err.appendLine(message)
            EXIT_FAILURE
        }
    }
}
