# 0003. Kotlin/JVM ツールチェーンとビルド構成

- Status: Accepted（2026-09-25、PR #1 の承認をもって人間の Architect が承認）
- Date: 2026-09-25

## Context

AGENTS.md は Kotlin/JVM を優先し、Java 25 を対象とし、Gradle Kotlin DSL と Wrapper を推奨している。README の Technology Stack には「MVP は Java 中心」という記述があり、AGENTS.md と一致していない。バージョンは動作確認をしてから固定する方針である。

## Decision

- 実装言語は Kotlin。JVM ツールチェーンは Java 25（`jvmToolchain(25)`）。
- Kotlin Gradle Plugin 2.3.21 を使う。Gradle 9.6.1 に同梱されている Kotlin と同じバージョンで、Java 25 のバイトコード（class file major version 69）を出力することを確認済み。
- Gradle 9.6.1 の Wrapper をコミットする。
- テストには `kotlin("test")`（JUnit Platform）のみを使う。それ以外の外部依存は追加しない。
- 共通のビルド設定は、ルートの `build.gradle.kts` の `subprojects` にまとめる。モジュール数が増えて設定が分岐するようになったら、convention plugin に移す。
- サンプルデータは Kotlin コードとして定義する。ファイル形式（JSON / YAML / RDF）の読み込みは、取り込み機能が必要になった時点で決める。

## Alternatives

- **Kotlin 2.4.x**: より新しいが、Gradle に同梱されている Kotlin とバージョンが食い違う。現時点で必要な機能もない。
- **Java のみで実装する**: README の記述とは一致するが、AGENTS.md の方針と一致しない。
- **Kotest / AssertJ などを使う**: 表現力は上がるが、現在のテストには必要ない。
- **サンプルデータを JSON にする**: 外部依存（シリアライザ）か独自のパーサが必要になる。

## Consequences

- 開発者には JDK 25 が必要になる。Gradle の toolchain 自動プロビジョニングは設定していない。
- README の「MVP は Java 中心」という記述は、この ADR の承認に合わせて Kotlin 優先の記述に更新した。

## Validation / Migration

- `./gradlew --version` で Gradle 9.6.1 と JVM 25 を確認した。
- `./gradlew build` で全モジュールのコンパイルとテストが成功した。
