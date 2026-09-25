package io.github.yamakenji.archknowledge.cli

import io.github.yamakenji.archknowledge.application.impact.AnalyzeImpact
import io.github.yamakenji.archknowledge.application.impact.ImpactQuery
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisOutcome
import io.github.yamakenji.archknowledge.examples.ordermanagement.OrderManagementExample
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile
import kotlin.system.exitProcess

const val EXIT_OK = 0
const val EXIT_FAILURE = 1
const val EXIT_USAGE = 2

private val USAGE = """
    Usage:
      arch-knowledge concepts                        List concepts in the Order Management example
      arch-knowledge impact <conceptId> [--depth N]  Show potential downstream impact of a concept
""".trimIndent()

fun main(args: Array<String>) {
    exitProcess(run(args.toList(), System.out, System.err))
}

/** Runs the CLI against the bundled Order Management example and returns the process exit code. */
fun run(args: List<String>, out: Appendable, err: Appendable): Int {
    val graph = OrderManagementExample.graph()
    val schema = BusinessSoftwareProfile.schema
    val violations = schema.validate(graph)
    if (violations.isNotEmpty()) {
        err.appendLine("Example model does not conform to its profile:")
        violations.forEach { err.appendLine("  ${it.message}") }
        return EXIT_FAILURE
    }

    return when (args.firstOrNull()) {
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
