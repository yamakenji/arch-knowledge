# 0005. Neo4j snapshot persistence

- Status: Proposed
- Date: 2026-09-30

## Context

The in-memory slice defines generic graph validation and deterministic impact evidence.
Step 1 needs persistence without changing Core, application services, or CLI behavior.
The human agreed to generic nodes/edges and validated snapshot loading; this record remains
Proposed pending formal architectural acceptance. Related decisions: [0001](0001-metamodel-agnostic-core-and-module-layout.md),
[0002](0002-impact-traversal-and-evidence-semantics.md), [0003](0003-kotlin-jvm-toolchain.md).

## Decision

- Add `:adapters:neo4j`, depending inward on Core and on Neo4j Java driver 5.26.0.
  Test-only Neo4j harness 5.26.0 provides isolated disposable integration databases.
- Persist `(:Concept {id, type, name, description, provenance})` and directed
  `[:RELATION {id, type, provenance}]`. Type IDs are properties, never dynamic labels
  or relationship names. Stable domain IDs are preserved; database internal IDs are not used.
- `save` imports a complete model: construct `InMemoryConceptGraph`, then use supplied
  `Schema.validate` before opening a write transaction. Replace Concept nodes and attached
  relationships plus all `RELATION` edges atomically in one managed transaction. This requires a dedicated database
  and a single writer; it is not an incremental upsert or multi-tenant store.
- `load` reads both collections in one Cypher statement in one managed read transaction,
  maps strict string properties, validates structure and Profile endpoints/types, and returns
  `InMemoryConceptGraph`. Relationships with non-Concept endpoints are rejected, not silently omitted.
  Empty databases yield valid empty snapshots. External concurrent mutation is unsupported;
  Neo4j read-committed isolation is not claimed to provide MVCC snapshot[0004-kotlin-2-4-upgrade.md](0004-kotlin-2-4-upgrade.md) isolation.
- All imported values use query parameters. The adapter never interprets business type names.
  Optional description/provenance preserve null versus empty strings.
- Return explicit invalid-data versus redacted infrastructure outcomes. The caller owns driver
  lifecycle and supplies authentication from environment. Schema definitions remain in Profiles,
  not stored enterprise nodes. No new outward dependency or persistence annotations enter Core.
- Analyze the loaded in-memory graph with existing Core logic, retaining all evidence semantics.
  No Cypher traversal or application/CLI integration is introduced in this step.

## Alternatives

- Profile-specific labels/relationship types: couples mapping to the metamodel and complicates safe queries.
- Direct database traversal: risks changing all-simple-path evidence and truncation semantics.
- Incremental merge: requires separate deletion, conflict and stale-state policies, outside this slice.
- Testcontainers: useful later; harness tests exercise real Bolt/Cypher without requiring Docker access.

## Consequences

Whole-model reads/writes require O(V + E) memory and suit small curated MVP models.
Replacement deletes attached edges, including manually created ones; dedicated database ownership is mandatory.
No uniqueness constraints are required for this replacement-only single-writer slice: validation rejects
duplicate IDs on import and load. Shared writers, migrations and indexed large-graph access require a later design.
The driver and harness add adapter-local dependency maintenance; version upgrades need contract tests.

## Validation / Migration

Adapter tests check stable IDs, Unicode/parameterized strings, optional fields, repeat replacement,
empty snapshots, unchanged impact analysis, a non-business schema, invalid imports preserving old data,
malformed persisted values, duplicate IDs, endpoint/type violations and infrastructure failures.
An induced database constraint failure verifies rollback preserves the previous snapshot.
Local Compose exposes only loopback ports and requires environment credentials.
Existing in-memory models require no migration; callers explicitly save them with their Profile schema.
No accepted ADR is superseded by this proposal.