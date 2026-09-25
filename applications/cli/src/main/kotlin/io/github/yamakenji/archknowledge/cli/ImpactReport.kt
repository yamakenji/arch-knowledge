package io.github.yamakenji.archknowledge.cli

import io.github.yamakenji.archknowledge.core.analysis.EvidencePath
import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysis
import io.github.yamakenji.archknowledge.core.model.Concept

object ImpactReport {
    fun format(analysis: ImpactAnalysis): String = buildString {
        val policy = analysis.policy
        appendLine("Start: ${label(analysis.start)}")
        appendLine(
            "Traversal: ${policy.direction} via ${policy.relationTypes.map { it.value }.sorted().joinToString(", ")}" +
                "; max depth ${policy.maxDepth}",
        )
        if (analysis.truncated) {
            appendLine(
                "Result: INCOMPLETE - depth limit ${policy.maxDepth} reached; " +
                    "more affected concepts or evidence paths may exist",
            )
        } else {
            appendLine("Result: complete within the registered relations")
        }
        appendLine()

        if (analysis.affected.isEmpty()) {
            appendLine("No affected concepts found through registered relations.")
            appendLine("This does not show that there is no impact; relations may be unregistered.")
        } else {
            appendLine("Affected concepts (${analysis.affected.size}):")
            analysis.affected.forEachIndexed { index, affected ->
                appendLine("  ${index + 1}. ${label(affected.concept)}  distance ${affected.distance}")
                affected.evidence.forEach { appendLine("       ${path(it)}") }
            }
        }
        appendLine()
        appendLine("Note: potential dependency impact derived from registered relations only;")
        appendLine("      it does not confirm actual business consequences.")
    }

    private fun label(concept: Concept) = "${concept.id} [${concept.type}] ${concept.name}"

    private fun path(path: EvidencePath): String = buildString {
        append(path.start)
        path.steps.forEach { step ->
            val edge = "${step.relation.type} ${step.relation.id}"
            append(if (step.alongStoredDirection) " -[$edge]-> " else " <-[$edge]- ")
            append(step.to)
        }
    }
}
