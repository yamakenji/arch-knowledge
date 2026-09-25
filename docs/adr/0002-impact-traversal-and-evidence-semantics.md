# 0002. 影響分析のトラバーサルと根拠経路の意味論

- Status: Proposed
- Date: 2026-09-25

## Context

MVP の問いは「この Capability を変更すると、何に影響する可能性があるか」である。結果は人間と将来の LLM の双方が根拠として使うため、同じ入力に対して同じ結果を返し、到達先ごとに登録済みの関係による経路を示す必要がある。永続化（Neo4j）を後から追加しても同じ意味論を保つため、先に定義を固定する。

## Decision

### 関係の保存方向と探索方向

- 関係は `source -> target` として保存する。保存方向の読み方は Profile の関係型が `meaning` として定義する。
- business-software Profile では次の向きで保存する（多対多）。

  | 関係型 | 保存方向 | 読み方 |
  | --- | --- | --- |
  | `REALIZED_BY` | Capability → BusinessProcess | Capability は BusinessProcess によって実現される |
  | `SUPPORTED_BY` | BusinessProcess → Application | BusinessProcess は Application によって支援される |
  | `IMPLEMENTS` | Application → BoundedContext | Application は BoundedContext（の一部）を実装する |

- 探索は `TraversalPolicy(relationTypes, direction, maxDepth)` で明示する。`direction` は `OUTGOING`（保存方向に従う）、`INCOMING`（逆向き）、`BOTH` のいずれかで、許可された全関係型に一律に適用する。
- Profile の既定 `downstreamImpact` は上記 3 関係型を `OUTGOING`、`maxDepth = 3` で辿る。
- 経路の各ステップは、辿った順（`from` → `to`）と、それが保存方向に沿うか（`alongStoredDirection`）を持つ。

### 結果の意味

1. **開始 Concept**: `start` として別に返し、`affected` には含めない。
2. **根拠経路**: 開始 Concept から、長さ `maxDepth` 以下の**単純経路**（同じ Concept を 2 回通らない経路）をすべて列挙する。
3. **重複排除**: 経路は relation ID の並びで識別し、一度だけ返す。同じ 2 Concept 間の並行する関係は、別の経路として返す。`affected` の Concept は ID で重複排除し、各 Concept はそこへの全経路を持つ。
4. **距離**: 各 Concept の `distance` は最短経路の長さ。
5. **循環・自己ループ**: 単純経路の規則により、その経路で探索を止める。これだけでは `truncated` にしない。
6. **深さ制限**: `maxDepth` は経路中の関係の本数。`0` なら何も辿らない。長さ `maxDepth` の経路が、単純経路の規則のもとでまだ延長できる場合に限り `truncated = true` とする。このとき、到達先の Concept か経路のどちらか（または両方）が欠けている可能性がある。
7. **順序**: `affected` は（`distance`、型 ID、Concept ID）の順。各 Concept の経路は（長さ、relation ID の並び）の順。ID は locale に依存しない文字列の辞書順で比較する。
8. **失敗の区別**: 要求の不備（許可関係型が空、`maxDepth < 0`、スキーマにない関係型、空の開始 ID）は `InvalidRequest`、開始 ID が存在しない場合は `StartConceptNotFound` を返す。例外は使わない。到達先がない場合は失敗ではなく空の `Analyzed` を返し、表示では「影響なし」と断定しない。

### 性能の前提

全単純経路の列挙は、最悪の場合に指数的に増える。MVP の小規模で整理されたモデルでは `maxDepth` によって十分に抑えられると想定する。モデルが大きくなった場合も、経路の意味論を黙って変えずに、上限と `truncated` の表示を明示的に追加する。

## Alternatives

- **開始 Concept を `affected` に含める**: 「変更対象そのもの」を影響先と混同しやすい。
- **Concept ごとに最短経路 1 本だけを返す**: 高速で結果も小さいが、分岐・合流したときの根拠が欠け、「なぜ影響するのか」の説明が不完全になる。
- **関係型ごとに方向を指定する**: より柔軟だが、現在の Profile では全関係型が `OUTGOING` で足りる。必要になった時点で `TraversalPolicy` を拡張する。
- **深さ制限を超えても到達先の Concept が変わらないなら `truncated = false` とする**: 経路が欠けていても完全と表示してしまうため、根拠の完全性という観点で誤解を招く。

## Consequences

- 同じグラフと同じ要求に対して、入力順に関係なく同じ結果を返す（テストで確認済み）。
- Neo4j 実装は、同じ単純経路の列挙、重複排除、順序、`truncated` の判定を再現する必要がある。Cypher の可変長パターンが同じ意味論を持つかは、実装時に契約テストで確認する。
- 大きなモデルでは経路数が増えるため、上限の設計が必要になる。

## Validation / Migration

- `core` の `ImpactAnalyzerTest` が、分岐・合流、並行する関係、循環、自己ループ、無関係なノード、開始 ID が存在しない場合、深さ 0・境界・超過、経路の欠落による `truncated`、方向、関係型の絞り込み、順序、入力順への非依存性、経路の整合性を検証する。
- `examples/order-management` のテストが、期待される結果を固定する。
