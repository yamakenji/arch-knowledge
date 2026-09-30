# 0006. Application-owned impact interpretation

- Status: Proposed
- Date: 2026-09-30

## Context

Natural-language input and model output are untrusted. The deterministic analysis and evidence semantics in [ADR 0002](0002-impact-traversal-and-evidence-semantics.md) must remain authoritative. The inward dependency boundary in [ADR 0001](0001-metamodel-agnostic-core-and-module-layout.md) excludes vendor APIs and Profile-specific names from application logic.

## Decision

- Application owns `ImpactIntentInterpreter`, accepting text only and returning `ImpactInterpretationOutcome`. Its structured `ImpactIntent` contains a raw operation and exactly one selector: stable concept ID or exact concept name. Only the case-sensitive operation `impact` is supported.
- The port has explicit malformed, unsupported, and infrastructure outcomes. Future adapters must reject invalid syntax, wrong field types, and unknown fields, including model-supplied depth, direction, relation types, query text, or policy. No provider payloads, exception messages, or credentials appear in these outcomes.
- `AnalyzeNaturalLanguageImpact` validates intent before resolving it against a validated snapshot. ID resolution has no name fallback; names use exact case-sensitive equality without trimming or fuzzy matching. Unknown selectors and nonpermitted seed types have distinct outcomes.
- Composition injects permitted seed types and an `AnalyzeImpact` instance using the same snapshot. Application does not enumerate business types. Within the permitted seed types, duplicate names return choices sorted by stable ID; no candidate is guessed.
- An optional human-selected ID must belong to the candidates for the currently interpreted, validated intent. Every execution reinterprets and revalidates the original text, including operation and selector, before checking selection. An old choice may be rejected if interpretation changes; callers must not send the selected ID as a replacement natural-language request. No caller-supplied ambiguity object or ID can bypass validation.
- Only the resolved ID is passed to `AnalyzeImpact` through `ImpactQuery`; all traversal options remain composition-owned defaults. Successful results preserve structured evidence, ordering, and incomplete status unchanged. Analysis rejection remains explicit.
- The model neither accesses nor mutates the graph, invents facts, nor decides traversal semantics. This slice implements no explanation generation or provider calls. A future OpenAI-compatible configured endpoint adapter implements the port, without changing its authority.

## Alternatives

- Let the model produce traversal options or Cypher: rejected because this delegates deterministic semantics and graph authority to untrusted output.
- Guess a duplicate name match: rejected because it can analyze the wrong concept.
- Trust a selected ID or caller-provided candidate list: rejected because either can bypass the original intent and permitted seed validation.
- Cache validated disambiguation sessions: could avoid reinterpretation, but adds session storage and expiry without a current requirement. Revalidation is fail-closed and requires no public trusted token.

## Consequences

No additional dependencies or Core changes are needed. Exact-name matching deliberately rejects approximate names. Name lookup scans the small MVP snapshot; selection may require renewed disambiguation if a provider changes its interpretation. Composition must pair the resolver and analyzer with the same immutable validated graph and supply the Profile's permitted seed types. Empty permitted types deny every seed.

## Validation / Migration

`./gradlew :application:test` exercises deterministic stubs for valid, malformed, unsupported, unknown, ambiguous, nonpermitted, and infrastructure responses. It checks selection cannot override intent, changed interpretations reject stale selections, and natural-language results equal full structured analysis results at multiple depth boundaries. Provider syntax/extra-field validation belongs in future adapter contract tests; live-model checks are not required here. Existing structured use cases remain unchanged. No accepted ADR is superseded; human acceptance of this proposal is still required.