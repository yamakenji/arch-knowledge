package io.github.yamakenji.archknowledge.application.impact

/** Untrusted model output. Exactly one seed selector must be supplied; only `impact` is permitted. */
data class ImpactIntent(
    val operation: String?,
    val startConceptId: String? = null,
    val startConceptName: String? = null,
)

sealed interface ImpactInterpretationOutcome {
    data class Interpreted(val intent: ImpactIntent) : ImpactInterpretationOutcome
    data object Malformed : ImpactInterpretationOutcome
    data object Unsupported : ImpactInterpretationOutcome
    data object InfrastructureFailure : ImpactInterpretationOutcome
}

/**
 * Interprets text only, without graph access or authority over traversal policy.
 * Adapters must reject malformed payloads, extra fields (including policy options), and wrong types.
 * Failure outcomes must not carry provider payloads, credentials, or exception messages.
 */
fun interface ImpactIntentInterpreter {
    fun interpret(text: String): ImpactInterpretationOutcome
}