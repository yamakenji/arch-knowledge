# Local Neo4j

Use a dedicated database for this slice; save replaces all `Concept` nodes and their attached edges.
Do not point the adapter at a shared or production database. Concurrent writers are not supported.

Set `NEO4J_USERNAME` to `neo4j` and `NEO4J_PASSWORD` to a locally chosen password (minimum eight characters)
in your shell environment, not in committed files. Then run from the repository root:

```bash
docker compose -f deployment/compose.yaml up -d
docker compose -f deployment/compose.yaml down
```

Bolt is available at `bolt://localhost:7687`, browser at `http://localhost:7474`.
The named volume survives shutdown. Initial authentication applies only to a fresh volume.
Never pass credentials as CLI arguments or log driver configuration.

There is intentionally no CLI wiring in Step 1. Composition code owns the Neo4j driver:
read `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, and optionally `NEO4J_DATABASE`
from environment, construct `GraphDatabase.driver(uri, AuthTokens.basic(username, password))`,
and close it after use. `Neo4jSnapshotStore(driver, database = "neo4j")` exposes
`save(concepts, relations, schema)` and `load(schema)`; results distinguish `Success`,
`InvalidData`, and redacted `InfrastructureFailure`. `load` returns a validated `InMemoryConceptGraph`.

```bash
./gradlew :adapters:neo4j:test
```

Tests start isolated disposable Neo4j 5.26.0 harness instances under the module build directory;
Docker and external credentials are not required. Java 25 and the committed wrapper are required.
The harness and driver versions match the pinned local server. Compose is only for manual use.