package io.github.yamakenji.archknowledge.cli

import io.github.yamakenji.archknowledge.application.impact.ImpactIntent
import io.github.yamakenji.archknowledge.application.impact.ImpactIntentInterpreter
import io.github.yamakenji.archknowledge.application.impact.ImpactInterpretationOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaturalLanguageCliTest {
    @Test
    fun `natural language uses the same evidence as structured analysis`() {
        val structured = StringBuilder()
        assertEquals(EXIT_OK, run(listOf("impact", "cap-order-management"), structured, StringBuilder()))
        val output = StringBuilder()
        val error = StringBuilder()
        assertEquals(EXIT_OK, run(
            listOf("ask", "What depends on order management?"), output, error,
            interpreterFactory = { ImpactIntentInterpreter {
                ImpactInterpretationOutcome.Interpreted(ImpactIntent("impact", startConceptId = "cap-order-management"))
            } },
        ))
        assertTrue(output.startsWith(structured.toString()))
        assertTrue(output.contains("provenance:"))
        assertTrue(output.contains("potential review targets"))
        assertFalse(output.contains("guaranteed"))
        assertEquals("", error.toString())
    }

    @Test
    fun `untrusted and failed interpretations do not produce evidence`() {
        val cases = listOf(
            ImpactInterpretationOutcome.Malformed to EXIT_FAILURE,
            ImpactInterpretationOutcome.Unsupported to EXIT_FAILURE,
            ImpactInterpretationOutcome.InfrastructureFailure to EXIT_INFRASTRUCTURE,
            ImpactInterpretationOutcome.Interpreted(ImpactIntent("impact", startConceptId = "unknown")) to EXIT_FAILURE,
        )
        cases.forEach { (interpretation, expected) ->
            val output = StringBuilder()
            val error = StringBuilder()
            assertEquals(expected, run(listOf("ask", "question"), output, error,
                interpreterFactory = { ImpactIntentInterpreter { interpretation } }))
            assertEquals("", output.toString())
            assertTrue(error.isNotEmpty())
        }
    }

    @Test
    fun `configuration errors are redacted and invalid arguments avoid model calls`() {
        val error = StringBuilder()
        assertEquals(EXIT_USAGE, run(listOf("ask", "question"), StringBuilder(), error,
            interpreterFactory = { throw IllegalArgumentException("secret-token") }))
        assertFalse(error.contains("secret-token"))
        assertEquals(EXIT_USAGE, run(listOf("ask", "question", "--depth", "2"), StringBuilder(), StringBuilder(),
            interpreterFactory = { error("Must not initialize model") }))
    }
}