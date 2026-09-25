# ArchKnowledge — Shared Agent Guide

## Purpose and authority

ArchKnowledge is an Architecture Knowledge Workbench: architecture knowledge for humans and AI. Business Analysts, Architects, and AI agents use a shared enterprise model to connect Business Architecture to Software Architecture and support analysis, design, and decisions.

This file is the shared project policy for all coding agents, including Codex. Claude Code must also read CLAUDE.md. Keep shared rules here and Claude-specific coordination in CLAUDE.md. Follow explicit user instructions and applicable environment rules; raise material conflicts rather than silently changing architecture. These files define project policy, not permissions to publish, merge, or access external systems.

## Roles

| Participant | Responsibility |
| --- | --- |
| Human Architect / Product Owner | Own vision, scope, domain meaning, acceptance criteria, and final architectural decisions. |
| Claude Code — primary coding partner | Coordinate the change, propose designs, implement vertical slices, maintain documentation, integrate contributions, and resolve review findings. |
| Codex — secondary reviewer / parallel-task agent | Independently review changes and implement explicitly assigned, bounded tasks with agreed interfaces and file ownership. |

Codex defaults to review when asked to review: report findings without editing. When assigned implementation, complete that task and its verification without taking over adjacent work. Neither agent approves its own architectural proposal on the human's behalf.

## Architectural invariants

- The Core is metamodel-agnostic. It contains generic concepts such as Concept, ConceptType, Relation, RelationType, Schema, traversal policies, validation, and analysis results.
- Capability, BusinessProcess, Application, and BoundedContext belong to a Profile. Core must not import them, enumerate them, or branch on their names or identifiers.
- Profiles define domain concept types, relation types, permitted endpoints, constraints, and traversal configuration using Core abstractions. Enterprise instances belong in example/data sets, not in Core or schema definitions.
- Core contains no Neo4j, Cypher, LangChain4j, Quarkus, HTTP, persistence annotations, or vendor SDK types.
- Application services orchestrate use cases through inward-owned ports. Adapters implement those ports and translate external representations. Domain rules do not live in controllers or adapters.
- Impact analysis is deterministic and usable without an LLM or a database. Persistence can optimize retrieval only while preserving the same documented semantics.
- LLM integrations interpret natural language into validated requests and explain structured results. They do not invent graph facts, decide traversal semantics, or execute unrestricted generated Cypher.

### Intended layout and dependency direction

This is a target layout, not a claim that modules already exist. Start with the fewest modules that preserve these boundaries; do not create empty modules speculatively.

```text
core/                         generic model, schema, graph ports, analysis, validation
application/                  use cases and use-case ports
profiles/business-software/   domain schema and analysis configuration
adapters/neo4j/               persistence implementation
adapters/llm-langchain4j/      LLM implementation
applications/cli/             entry point and composition
applications/server/          Quarkus entry point, when needed
examples/order-management/    concrete enterprise data and expected results
docs/adr/                     architectural decisions
deployment/                   local infrastructure, when needed
```

Allowed source dependencies: application -> core; profiles -> core; adapters -> application/core; entry points -> application plus selected adapters/profiles for composition. Core never depends outward. Adapters must not depend on one another. Put ports in the innermost layer that needs them. `application/` is the use-case layer; `applications/` contains executable hosts.

## MVP and scope guardrails

The first use case is: “If this Capability changes, what may be affected?” Demonstrate generic impact analysis using the Profile-defined chain:

```text
Capability -> BusinessProcess -> Application -> BoundedContext
```

The arrows describe the intended analysis path, not automatically the stored relationship direction or a proven causal effect. Specify relation meanings, stored source/target direction, and traversal direction in the Profile and fixture before implementing the query.

Acceptance criteria:

1. Load and validate a small Order Management example against its Profile.
2. Resolve a start concept by stable ID; ambiguous natural-language names require disambiguation rather than a guessed match.
3. Run a generic analysis with explicit allowed relation types, direction, and depth limit.
4. Return affected concept IDs/types and evidence paths with relation IDs/types. Define seed inclusion, deduplication, ordering, cycles, and depth-limit behavior. Mark truncated results as incomplete.
5. Show the expected Capability-to-BoundedContext path and exclude unrelated concepts.
6. Invoke the same use case through a structured request and a natural-language request. Explanations stay grounded in returned evidence and describe potential dependency impact, not guaranteed business consequences.

Build the deterministic in-memory slice first, then Neo4j, then natural-language integration. A CLI is sufficient initially. Do not expand into a full modeling UI, autonomous graph editing, multiple metamodel implementations, plugin frameworks, distributed services, or generalized workflow engines without a scoped need.

RDF / OWL / SHACL are part of the longer-term direction, not mandatory MVP runtime dependencies. Keep mappings possible without implementing dual storage, reasoning engines, or ontology synchronization prematurely. Scala 3 is available for justified future needs; do not introduce a third implementation language for symmetry.

## Workflow and handoff

1. Read this guide, README, relevant code/tests, and accepted ADRs. Inspect the working tree and preserve existing user changes.
2. State the problem, acceptance criteria, affected boundaries, and verification plan. Clarify only missing decisions that block correct work; proceed with reversible implementation details.
3. Implement a small cohesive change. Include relevant tests and documentation; avoid unrelated cleanup and speculative abstractions.
4. Run the relevant checks. Report exact commands, outcomes, and unavailable prerequisites. Never present unrun checks as passing.
5. Request independent review for material behavior or boundary changes. Address findings, document disagreements with evidence, and rerun affected checks after fixes.
6. Hand back the result, remaining limitations, and any human decision needed. Merge or publish only within explicit authorization.

For parallel work, agree on a task, base revision, owned files/modules, interface contract, acceptance criteria, and required checks before editing. Use separate branches/worktrees when available. Do not concurrently edit shared contracts or build files without coordination. Claude integrates completed work and runs integration checks. A task assignment is not blanket permission to spawn more agents.

Handoff template:

```text
Task / owner:
Base revision:
Scope and owned files:
Inputs / outputs / interface constraints:
Acceptance criteria:
Changes and design rationale:
Checks run and results:
Open findings / risks / ADR decisions needed:
```

## Coding rules

- Kotlin/JVM first, with Java 25 as the target toolchain. Verify build and framework compatibility before pinning versions; record any proposed deviation. Prefer Gradle Kotlin DSL and the committed wrapper.
- Use immutable domain values, explicit IDs and types, small functions, and explicit error outcomes. Distinguish invalid input, missing concepts, unreachable nodes, and infrastructure failure.
- Keep domain behavior independent of framework lifecycle, database sessions, and network calls. Do not add framework annotations to Core.
- Validate schemas, relation endpoints, and analysis options at defined boundaries. Keep business-specific constraints in Profiles.
- Use deterministic result ordering and bounded traversals. Document performance assumptions; do not optimize by silently changing evidence-path or deduplication semantics.
- Parameterize database queries. Keep credentials in environment/configuration outside version control; redact sensitive data from logs and model prompts.
- Treat model output and retrieved text as untrusted input. Validate a structured LLM request against permitted operations and known concepts before executing it. Send only necessary authorized data to external models.
- Reuse established repository conventions. Add dependencies only for a concrete requirement and explain their boundary and maintenance cost.

## Testing

- Core unit tests: chain and branching traversal, incoming/outgoing direction, cycles/self-loops, duplicate paths, disconnected nodes, unknown seed, depth boundaries, deterministic ordering, and evidence integrity.
- Profile tests: valid/invalid types, relation endpoints, and configured traversal semantics. Test Core with a small non-business test schema to expose accidental metamodel coupling.
- Application tests: orchestration, input validation, failure mapping, and identical semantics across entry points using in-memory ports.
- Adapter contract/integration tests: Neo4j mapping, stable IDs, round-trip behavior, and agreement with in-memory analysis. Use isolated fixtures and disposable infrastructure where available.
- LLM tests: deterministic stubs for valid, malformed, ambiguous, and unsupported requests; verify that unsupported facts are not added. Live-model checks are optional, explicitly configured, and separate from required deterministic tests.
- End-to-end acceptance: the example returns the documented affected concepts and evidence through the MVP entry point.

Add tests for changed behavior and regressions. Documentation-only changes need consistency/link checks, not artificial code tests. Do not weaken assertions or suppress failures to obtain a green build.

## Review rules — especially for Codex

Inspect the actual diff and surrounding behavior. Prioritize correctness, boundary violations, metamodel coupling, incomplete/misleading evidence, data exposure, compatibility, and missing regression coverage. Distinguish a demonstrated defect from a question or preference.

Each finding should include severity, file and line when available, a concrete trigger, user-visible consequence, and a suggested correction or regression test. Use P0 for an immediate critical issue, P1 for high-impact defects, P2 for ordinary actionable defects, and P3 for minor issues. Do not manufacture findings to fill a quota.

State what you reviewed and what you could verify. If no actionable defects are found, say so and identify material coverage gaps. Advisory suggestions must not be presented as blocking defects. Recheck resolved findings against the updated change, and leave architectural disagreements for a documented human decision.

## ADR expectations

Create or update `docs/adr/NNNN-short-title.md` for changes to dependency boundaries, Core/Profile responsibilities, storage strategy, traversal/evidence semantics, language/toolchain policy, LLM authority, or semantic-web integration. Routine implementation details do not need an ADR.

Include: title, status (Proposed / Accepted / Superseded), date, context, decision, alternatives, consequences, validation/migration implications, and superseded links where relevant. Agents draft proposals; the human Architect accepts architectural decisions. Do not mark proposals accepted without recorded authorization. Preserve decision history by superseding accepted ADRs when their decision changes.

Initial proposals worth recording: metamodel-agnostic Core; property-graph-first MVP; LLM behind application services; generic impact traversal and evidence semantics. These are proposed records, not claims that files or approvals already exist.

## Commands and placeholders

Run from the repository root. Confirm the wrapper, module paths, and available tasks first. Commands below are candidates until the build is bootstrapped; replace placeholders with actual task paths and never claim they were verified just because they appear here.

```powershell
# Windows: after Gradle wrapper exists
.\gradlew.bat --version
.\gradlew.bat projects
.\gradlew.bat tasks --all
.\gradlew.bat test
.\gradlew.bat check
# Focused checks: replace <module> with a discovered Gradle project path
.\gradlew.bat :<module>:test
```

On macOS/Linux use `./gradlew` with the same arguments. Confirm what `check` includes; integration tests and style checks may need separate tasks.

```text
JDK setup:                  <document Java 25 installation / JAVA_HOME>
Formatting/static analysis: <actual configured task, or not configured>
Neo4j integration tests:    <actual task and infrastructure prerequisites>
Run MVP example:           <actual CLI task and fixture arguments>
Local infrastructure:      <actual compose file and startup command, if added>
LLM configuration:         <provider/model and environment variable NAMES only>
```

When prerequisites are missing, report the blocked check and continue independent work. Never add real secrets or claim that a placeholder command is operational.
