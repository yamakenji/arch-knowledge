package io.github.yamakenji.archknowledge.application.impact

import io.github.yamakenji.archknowledge.core.analysis.ImpactAnalysisOutcome
import io.github.yamakenji.archknowledge.core.graph.ConceptGraph
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId

data class ImpactSeedChoice(val id: ConceptId, val type: ConceptTypeId, val name: String)

sealed interface NaturalLanguageImpactOutcome {
    data class Analyzed(val result: ImpactAnalysisOutcome.Analyzed) : NaturalLanguageImpactOutcome
    data class Ambiguous(val choices: List<ImpactSeedChoice>) : NaturalLanguageImpactOutcome
    data object InvalidRequest : NaturalLanguageImpactOutcome
    data object MalformedInterpretation : NaturalLanguageImpactOutcome
    data object UnsupportedRequest : NaturalLanguageImpactOutcome
    data object UnknownConcept : NaturalLanguageImpactOutcome
    data object SeedTypeNotPermitted : NaturalLanguageImpactOutcome
    data object InvalidSelection : NaturalLanguageImpactOutcome
    data object InterpretationFailure : NaturalLanguageImpactOutcome
    data class AnalysisRejected(val result: ImpactAnalysisOutcome) : NaturalLanguageImpactOutcome
}

/**
 * Resolves untrusted intent against the same validated snapshot used by [analyzeImpact].
 * Composition supplies permitted seed types; the interpreter receives no graph data.
 * Selection is checked against a freshly interpreted and validated request, never used as an override.
 */
class AnalyzeNaturalLanguageImpact(
    private val interpreter: ImpactIntentInterpreter,
    private val graph: ConceptGraph,
    private val analyzeImpact: AnalyzeImpact,
    permittedSeedTypes: Set<ConceptTypeId>,
) {
    private val permittedSeedTypes = permittedSeedTypes.toSet()

    fun execute(text: String, selectedConceptId: String? = null): NaturalLanguageImpactOutcome {
        if (text.isBlank()) return NaturalLanguageImpactOutcome.InvalidRequest
        val interpretation = try {
            interpreter.interpret(text)
        } catch (_: Exception) {
            return NaturalLanguageImpactOutcome.InterpretationFailure
        }
        val intent = when (interpretation) {
            is ImpactInterpretationOutcome.Interpreted -> interpretation.intent
            ImpactInterpretationOutcome.Malformed -> return NaturalLanguageImpactOutcome.MalformedInterpretation
            ImpactInterpretationOutcome.Unsupported -> return NaturalLanguageImpactOutcome.UnsupportedRequest
            ImpactInterpretationOutcome.InfrastructureFailure -> return NaturalLanguageImpactOutcome.InterpretationFailure
        }
        if (intent.operation.isNullOrBlank()) return NaturalLanguageImpactOutcome.MalformedInterpretation
        if (intent.operation != "impact") return NaturalLanguageImpactOutcome.UnsupportedRequest
        if ((intent.startConceptId == null) == (intent.startConceptName == null) ||
            intent.startConceptId?.isBlank() == true || intent.startConceptName?.isBlank() == true
        ) {
            return NaturalLanguageImpactOutcome.MalformedInterpretation
        }

        val matches = if (intent.startConceptId != null) {
            listOfNotNull(graph.concept(ConceptId(intent.startConceptId)))
        } else {
            graph.concepts().filter { it.name == intent.startConceptName }
        }
        if (matches.isEmpty()) return NaturalLanguageImpactOutcome.UnknownConcept
        val candidates = matches.filter { it.type in permittedSeedTypes }.sortedBy { it.id.value }
        if (candidates.isEmpty()) return NaturalLanguageImpactOutcome.SeedTypeNotPermitted
        val seed = if (selectedConceptId != null) {
            candidates.singleOrNull { it.id.value == selectedConceptId }
                ?: return NaturalLanguageImpactOutcome.InvalidSelection
        } else {
            if (candidates.size > 1) {
                return NaturalLanguageImpactOutcome.Ambiguous(
                    candidates.map { ImpactSeedChoice(it.id, it.type, it.name) },
                )
            }
            candidates.single()
        }
        return when (val result = analyzeImpact.execute(ImpactQuery(seed.id.value))) {
            is ImpactAnalysisOutcome.Analyzed -> NaturalLanguageImpactOutcome.Analyzed(result)
            else -> NaturalLanguageImpactOutcome.AnalysisRejected(result)
        }
    }
}