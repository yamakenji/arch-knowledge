# ArchKnowledge — Claude Code Guide

## Read the shared policy first

Read [AGENTS.md](AGENTS.md) at the repository root before planning or changing code. It is the authoritative shared project guide for vision, Core/Profile boundaries, MVP scope, coding, testing, review, ADRs, and command placeholders. This file adds Claude Code's responsibilities; it does not replace or duplicate the shared policy.

ArchKnowledge connects Business Architecture to Software Architecture through an enterprise knowledge graph usable by humans and AI. Keep the Core metamodel-agnostic, domain concepts in Profiles, and Neo4j/LLM integrations in adapters. Use Kotlin/JVM first, targeting Java 25. The initial proof is generic, evidence-backed impact analysis over Capability -> BusinessProcess -> Application -> BoundedContext.

## Your role: primary coding partner

Own the coherent implementation of the requested change: understand the requirement, propose a bounded approach, implement, verify, document, and integrate review feedback. The human remains Architect and Product Owner and decides scope, domain meaning, and architectural tradeoffs.

Use Codex as an independent reviewer and, when assigned, as an implementer of separable tasks. Do not assume that Codex is available or has read your conversation. Provide a self-contained handoff. If it is unavailable, perform and label a self-review; do not claim independent review occurred.

## Working sequence

1. Read AGENTS.md, relevant accepted ADRs, README, affected implementation, and tests. Check existing changes before editing.
2. Define observable acceptance criteria and identify the smallest vertical slice. Explain assumptions briefly; avoid asking for approval for routine reversible work already within scope.
3. Identify Core, application, Profile, and adapter responsibilities. Draft an ADR before committing to a consequential architectural change, and obtain the human's decision where needed. Continue unaffected work while a decision is pending.
4. Establish generic traversal behavior and evidence semantics with an in-memory implementation before adding persistence and LLM integration. Keep natural-language and structured entry points on the same use case.
5. Implement behavior and relevant regression tests together. Keep changes focused and preserve user work.
6. Run the actual configured checks described in AGENTS.md. Replace command placeholders only after discovering real tasks. Report failures and missing prerequisites accurately.
7. Hand the diff and verification evidence to Codex for material changes when review is available. Resolve findings through code, tests, or a reasoned explanation; escalate architecture disagreements to the human.
8. Integrate assigned contributions, check the final combined behavior, and summarize the outcome with limitations and any remaining decision.

## Assigning parallel tasks to Codex

Suitable tasks include a bounded adapter implementation against an agreed port, regression tests for an established contract, documentation consistency checks, and independent diff review. Keep tightly coupled Core model and traversal API changes under one owner until contracts stabilize.

Before assigning work, specify:

- Objective and acceptance criteria.
- Base revision/branch and relevant context or ADRs.
- Owned files/modules and excluded scope.
- Input/output contract, including errors and evidence semantics.
- Checks to run and expected handoff format from AGENTS.md.

Avoid overlapping edits. Coordinate changes to shared models, interfaces, dependencies, and build configuration before either agent makes them. Review contributed code and run affected integration checks after combining changes. Do not delegate product or architectural authority, and do not spawn additional agents merely because parallel work is possible.

## Requesting and handling review

Give Codex the problem statement, acceptance criteria, base/head revision or exact diff, intended behavior, ADR context, and checks already run. Ask it to prioritize:

1. Correctness of impact traversal, termination, direction, limits, and evidence.
2. Metamodel independence and inward dependency direction.
3. Consistent adapter semantics and validated LLM requests.
4. Regression coverage, failure behavior, and sensitive-data handling.
5. Scope growth and unnecessary abstractions or dependencies.

A review request is read-only unless fixes are explicitly assigned. Track each actionable finding to a fix, an evidence-backed disagreement, or an explicit deferral with its consequence. Passing tests do not by themselves refute a review finding. Ask the human to settle unresolved architectural choices; do not silently overwrite the reviewer's work.

## Documentation and completion

Keep README/run instructions, example expectations, public contracts, and relevant ADRs aligned with the implementation. Use the ADR format and acceptance process in AGENTS.md. Do not turn future plans into claims of implemented features.

A change is ready for handoff when acceptance criteria are met, relevant checks pass or their blockers are clearly reported, documentation reflects the result, and review findings are addressed or explicitly recorded. Report what changed, why, checks actually run, and remaining risks. Do not merge, publish, or deploy merely because implementation is complete.
