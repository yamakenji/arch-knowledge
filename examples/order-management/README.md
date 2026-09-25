# Order Management example

架空の企業モデルです。business-software Profile に適合しており（テストで検証しています）、定義は [OrderManagementExample.kt](src/main/kotlin/io/github/yamakenji/archknowledge/examples/ordermanagement/OrderManagementExample.kt) にあります。

## データ

```mermaid
flowchart LR
    cap[cap-order-management<br/>受注管理] -->|rel-001 REALIZED_BY| intake[bp-order-intake<br/>注文受付]
    cap -->|rel-002 REALIZED_BY| fulfil[bp-order-fulfillment<br/>出荷手配]
    intake -->|rel-003 SUPPORTED_BY| oms[app-oms<br/>Order Management System]
    intake -->|rel-004 SUPPORTED_BY| crm[app-crm<br/>Customer Relationship Management]
    fulfil -->|rel-005 SUPPORTED_BY| oms
    fulfil -->|rel-006 SUPPORTED_BY| wms[app-wms<br/>Warehouse Management System]
    oms -->|rel-007 IMPLEMENTS| bcOrder[bc-order<br/>Order Context]
    crm -->|rel-008 IMPLEMENTS| bcCustomer[bc-customer<br/>Customer Context]
    wms -->|rel-009 IMPLEMENTS| bcShipping[bc-shipping<br/>Shipping Context]

    hr[cap-hr-management<br/>人事管理] -->|rel-101 REALIZED_BY| payroll[bp-payroll<br/>給与計算]
    payroll -->|rel-102 SUPPORTED_BY| hris[app-hris<br/>HR Information System]
    hris -->|rel-103 IMPLEMENTS| bcPayroll[bc-payroll<br/>Payroll Context]
```

- 分岐: Capability から 2 つの BusinessProcess、各 BusinessProcess から 2 つの Application に分かれる。
- 合流: `app-oms` は 2 つの BusinessProcess から支援関係を持つ。そのため `app-oms` と `bc-order` には根拠経路が 2 本ずつある。
- 無関係なノード: 人事管理の系列は Order Management と接続していない。

## 期待される結果

`cap-order-management` を起点に、Profile の既定の探索（`downstreamImpact`: 3 関係型、`OUTGOING`、`maxDepth = 3`）を行った場合:

| # | Concept | 型 | distance | 根拠経路（relation ID） |
| --- | --- | --- | --- | --- |
| 1 | bp-order-fulfillment | BusinessProcess | 1 | rel-002 |
| 2 | bp-order-intake | BusinessProcess | 1 | rel-001 |
| 3 | app-crm | Application | 2 | rel-001, rel-004 |
| 4 | app-oms | Application | 2 | rel-001, rel-003 / rel-002, rel-005 |
| 5 | app-wms | Application | 2 | rel-002, rel-006 |
| 6 | bc-customer | BoundedContext | 3 | rel-001, rel-004, rel-008 |
| 7 | bc-order | BoundedContext | 3 | rel-001, rel-003, rel-007 / rel-002, rel-005, rel-007 |
| 8 | bc-shipping | BoundedContext | 3 | rel-002, rel-006, rel-009 |

- 結果は完全（`truncated = false`）。
- 人事管理系列の 4 Concept は含まれない。
- `maxDepth = 2` の場合は BoundedContext を除く 5 件になり、`truncated = true`（不完全）となる。

これらの期待値は `OrderManagementExampleTest` と `CliTest` で固定しています。
