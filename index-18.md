# Introduction to ZIO JSON

> [ZIO JSON](https://github.com/zio/zio-json) is a fast and secure JSON library with tight ZIO integration.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-json/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-json_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-json_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-json_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-json_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-json_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-json_2.13) [![ZIO JSON](https://img.shields.io/github/stars/zio/zio-json?style=social)](https://github.com/zio/zio-json)

## Introduction

The goal of this project is to create the best all-round JSON library for Scala:

- **Performance** to handle more requests per second than the incumbents, i.e. reduced operational costs.
- **Security** to mitigate against adversarial JSON payloads that threaten the capacity of the server.
- **Fast Compilation** no shapeless, no type astronautics.
- **Future-Proof**, prepared for Scala 3 and next-generation Java.
- **Simple** small codebase, concise documentation that covers everything.
- **Helpful errors** are readable by humans and machines.
- **ZIO Integration** so nothing more is required.

## Installation

Add the core module to your `build.sbt`. It is published for Scala 2.12, 2.13 and 3:

```scala
libraryDependencies ++= Seq(
  // Codecs, derivation, the Json AST and, on the JVM, streaming (JVM, Scala.js, Scala Native)
  "dev.zio" %% "zio-json" % "1.1.3"
)
```

In a Scala.js or Scala Native project, use `%%%`:

```scala
libraryDependencies ++= Seq(
  // Codecs, derivation and the Json AST
  "dev.zio" %%% "zio-json" % "1.1.3"
)
```

[Getting Started](getting-started.md) continues from here with a first program.

## Modules

The optional modules below have the same version number as `zio-json` and depend on it, so adding one also brings in the core module. Add only the lines you need.

### Formats

Read and write YAML with your JSON codecs. This module is JVM only:

```scala
libraryDependencies ++= Seq(
  // Read and write YAML with your JSON codecs, through SnakeYAML (JVM)
  "dev.zio" %% "zio-json-yaml" % "1.1.3"
)
```

See [YAML](reference/yaml.md).

### Derivation

Generate codec instances with the `@jsonDerive` annotation. This module is published for Scala 2.12 and 2.13 only, and needs macro annotations turned on: the `-Ymacro-annotations` compiler option on Scala 2.13, or the macro paradise plugin on Scala 2.12 (the [@jsonDerive page](reference/zio-json-macros.md#installation) has both settings). Use `%%%` on Scala.js and Scala Native:

```scala
libraryDependencies ++= Seq(
  // The @jsonDerive annotation, which writes codec instances for you (JVM, Scala.js, Scala Native; Scala 2 only)
  "dev.zio" %% "zio-json-macros" % "1.1.3"
)
```

See [@jsonDerive Macro Annotation](reference/zio-json-macros.md).

### Integrations

Codecs for types from other libraries:

```scala
libraryDependencies ++= Seq(
  // http4s 0.23 entity encoders and decoders built from ZIO JSON codecs (JVM)
  "dev.zio" %% "zio-json-interop-http4s"     % "1.1.3",

  // Codecs and map-key codecs for refined types (JVM, Scala.js, Scala Native)
  "dev.zio" %% "zio-json-interop-refined"    % "1.1.3",

  // Codecs for Scalaz 7 IList (JVM, Scala.js, Scala Native; Scala 2 only)
  "dev.zio" %% "zio-json-interop-scalaz7x"   % "1.1.3",

  // Codecs and map-key codecs for enumeratum enums (JVM, Scala.js, Scala Native)
  "dev.zio" %% "zio-json-interop-enumeratum" % "1.1.3"
)
```

The http4s module is JVM only. The others are also built for Scala.js and Scala Native:

```scala
libraryDependencies ++= Seq(
  // Codecs and map-key codecs for refined types
  "dev.zio" %%% "zio-json-interop-refined"    % "1.1.3",

  // Codecs for Scalaz 7 IList (Scala 2 only)
  "dev.zio" %%% "zio-json-interop-scalaz7x"   % "1.1.3",

  // Codecs and map-key codecs for enumeratum enums
  "dev.zio" %%% "zio-json-interop-enumeratum" % "1.1.3"
)
```

See [HTTP4s Interop](interop/http4s.md), [Refined Interop](interop/refined.md), [Scalaz 7.x Interop](interop/scalaz-7x.md) and [Enumeratum Interop](interop/enumeratum.md).

### Testing

Golden (snapshot) tests that catch unintended changes to encoded JSON. This module is JVM only and belongs in the `Test` configuration:

```scala
libraryDependencies ++= Seq(
  // Golden tests for JSON codecs, built on ZIO Test (JVM)
  "dev.zio" %% "zio-json-golden" % "1.1.3" % Test
)
```

See [Golden Testing API](reference/golden-testing.md) and [Catch Unintended Changes to Your JSON with Golden Tests](how-to/golden-testing.md).

## Documentation

The documentation is split by what you want to do:

- **[Getting Started](getting-started.md)** installs ZIO JSON and walks through encoding and decoding your first types.
- **[Concepts](explanation/index.md)** explain the design and the trade-offs: how derivation and decoding work, Scala 2 and Scala 3 differences, decoder strictness, JSON shapes, performance and security.
- **[Reference](reference/index.md)** pages list every type, method, annotation and option.
- **[Integrations](interop/index.md)** connect ZIO JSON to http4s, refined, Scalaz 7 and enumeratum.
- **Tutorials** teach a larger example step by step: [Model a Real API Payload](tutorial/model-an-api-payload.md).
- **[How-to Guides](how-to/index.md)** solve one task each, such as renaming fields, encoding sealed traits or streaming large JSON documents.
