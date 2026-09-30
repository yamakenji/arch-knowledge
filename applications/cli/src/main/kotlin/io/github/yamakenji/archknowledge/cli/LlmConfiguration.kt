package io.github.yamakenji.archknowledge.cli

import io.github.yamakenji.archknowledge.adapters.llm.LangChain4jImpactIntentInterpreter
import io.github.yamakenji.archknowledge.adapters.llm.OpenAiImpactConfiguration
import io.github.yamakenji.archknowledge.application.impact.ImpactIntentInterpreter

internal fun configuredInterpreter(environment: Map<String, String>): ImpactIntentInterpreter {
    val keys = listOf("LLM_BASE_URL", "LLM_MODEL", "LLM_API_KEY")
    require(keys.all { !environment[it].isNullOrBlank() })
    return LangChain4jImpactIntentInterpreter(OpenAiImpactConfiguration(
        endpoint = environment.getValue("LLM_BASE_URL"),
        model = environment.getValue("LLM_MODEL"),
        apiKey = environment.getValue("LLM_API_KEY"),
    ))
}