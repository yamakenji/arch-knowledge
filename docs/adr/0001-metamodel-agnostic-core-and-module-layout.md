# 0001. メタモデル非依存の Core と初期モジュール構成

- Status: Accepted（2026-09-25、PR #1 の承認をもって人間の Architect が承認）
- Date: 2026-09-25

## Context

ArchKnowledge は Business Architecture と Software Architecture を一つのグラフでつなぐ。Capability などのドメイン概念は今後変わり得るし、別の Profile が追加される可能性もある。Core がドメイン概念に依存すると、Profile の追加・変更のたびに Core の変更が必要になり、Neo4j や LLM の統合でもドメイン固有の分岐が広がる。

## Decision

- Core は `Concept` / `Relation` / `ConceptTypeId` / `RelationTypeId` / `Schema` / `ConceptGraph` / `ImpactAnalyzer` などの汎用概念だけを持つ。型 ID の値を解釈・列挙・分岐しない。
- ドメインの型、関係、許可される端点、既定の探索設定は Profile（`profiles/business-software`）が Core の抽象で定義する。
- 企業インスタンスは Profile とも Core とも分け、`examples/order-management` に置く。
- 初期の Gradle モジュールは次の 5 つ。依存は内向きのみ。

| モジュール | 依存先 | 責務 |
| --- | --- | --- |
| `:core` | なし（kotlin-stdlib のみ） | 汎用モデル、スキーマ検証、グラフポート、インメモリ実装、影響分析 |
| `:application` | core | ユースケース `AnalyzeImpact`（既定値の適用、スキーマに基づく要求検証） |
| `:profiles:business-software` | core | 4 概念型・3 関係型・既定トラバーサル |
| `:examples:order-management` | core, profile | 架空の企業データ |
| `:applications:cli` | すべて | 合成と表示 |

- インメモリのグラフ実装は外部技術を含まないため Core に置く。Neo4j などの実装は将来 `adapters/` に置き、同じ `ConceptGraph` の意味論を守る。
- Core のテストは業務と無関係な合成スキーマ（`Node` / `LINK` / `OTHER`）で行い、メタモデルへの結合を検出する。

## Alternatives

- **単一モジュール**: ファイル数は減るが、Core から Profile を参照してもビルドで検出できない。
- **Core にドメイン enum を持つ**: 実装は単純だが、Profile 追加時に Core 変更が必要になり、AGENTS.md の不変条件に反する。
- **インメモリ実装を adapter モジュールに分離**: 境界は明確になるが、現時点では外部依存がなく、モジュールを増やす利点が小さい。

## Consequences

- Profile の型 ID は文字列の値オブジェクトであり、コンパイル時の型安全性は Profile 内の定数に依存する。
- モジュール間の依存方向は Gradle の依存宣言で強制される。
- `application` と `applications` の名前が近く、混同しやすい（AGENTS.md の命名に従う）。

## Validation / Migration

- `./gradlew :core:dependencies` で Core の依存が kotlin-stdlib のみであることを確認する。
- Core のソースに Profile の型名が現れないことをレビューで確認する。
