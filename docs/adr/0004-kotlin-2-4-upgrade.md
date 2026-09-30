# 0004. Kotlin 2.4 への更新

- Status: Proposed
- Date: 2026-09-30

## Context

ユーザーから Kotlin 2.4 を使うよう変更する依頼があった。[ADR 0003](0003-kotlin-jvm-toolchain.md) は Kotlin Gradle Plugin 2.3.21 を指定している。既存の Java 25、Gradle Wrapper 9.6.1、モジュール境界は変更しない。

## Decision

- 全モジュールで Kotlin Gradle Plugin 2.4.0 を使う。
- Java 25 ツールチェーンと Gradle Wrapper 9.6.1 を維持する。
- Gradle 同梱の Kotlin（Kotlin DSL 用）と、プロジェクトのコンパイラのバージョンを一致させることは必須としない。
- 承認時には ADR 0003 の Kotlin バージョン選定部分のみを置き換える。他の決定は維持する。本記録は Architect の承認待ちとし、既存の Accepted 記録は書き換えない。

## Alternatives

- Kotlin 2.3.21 を維持する：変更依頼を満たさない。
- Gradle Wrapper も更新する：今回の依頼には不要な変更範囲を追加する。

## Consequences

- コンパイラ、標準ライブラリ、`kotlin("test")` は 2.4.0 に揃う。
- `./gradlew --version` の Kotlin 表示は Gradle 同梱版の 2.3.21 のままになる。
- [Kotlin 2.4.0 の公式ドキュメント](https://kotlinlang.org/docs/whatsnew24.html#gradle)の Gradle 完全互換範囲は 7.6.3〜9.5.0。より新しい Gradle も利用可能だが、9.6.1 との組み合わせは本プロジェクトのビルドとテストで確認する。

## Validation / Migration

- JDK 25.0.2 / Gradle 9.6.1 で `./gradlew build :core:dependencies --configuration runtimeClasspath :applications:cli:run --args="impact cap-order-management"` を実行し、成功した。
- 全モジュールのコンパイルと既存テストが成功し、Core の標準ライブラリは 2.4.0 に解決された。
- CLI のサンプルは影響候補 8 件と根拠経路を返し、結果は complete だった。
- ドメインモデルやデータの移行は不要。