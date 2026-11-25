# Introduction to ZIO Parser

> Library for constructing parsers and pretty printers based on invertible syntax descriptions

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-parser/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-parser_3.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-parser_3) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-parser_3%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-parser_3/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-parser_3/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-parser_3) [![ZIO Parser](https://img.shields.io/github/stars/zio/zio-parser?style=social)](https://github.com/zio/zio-parser)

## Introduction

ZIO Parser describes a grammar once, as a `Syntax`, and uses that description in both directions: it parses text into values and prints values back to text. Because one value drives both, a parser and its pretty printer cannot drift apart.

The library gives you small building blocks (characters, strings, regexes), operators to combine them, and error values that say where and why a parse failed.

## Installation

Add ZIO Parser to your `build.sbt`. It is published for Scala 2.12, 2.13 and 3.

On the JVM, use `%%`:

```scala
libraryDependencies ++= Seq(
  // ZIO Parser: syntaxes, parsers, printers and regexes (JVM)
  "dev.zio" %% "zio-parser" % "0.1.13"
)
```

On Scala.js and Scala Native, use `%%%`:

```scala
libraryDependencies ++= Seq(
  // ZIO Parser: syntaxes, parsers, printers and regexes (Scala.js and Scala Native)
  "dev.zio" %%% "zio-parser" % "0.1.13"
)
```

## Where to Go Next

- [Getting Started](getting-started.md): parse and print your first value.
- [Printing and Invertible Syntaxes](printing.md): the idea behind one syntax that both parses and prints.
- [Syntax](syntax.md), [Regex](regex.md), [Error Handling](errors.md), [Parser Reference](reference-parser.md) and [Printer Reference](reference-printer.md): the API.
- [Tutorial: Parse and Print a Config Language](tutorial.md), [Example: a JSON Syntax](example-json.md) and [Example: a GraphQL Parser](example-caliban.md): step-by-step learning.
- [How-to Guides](how-to-whitespace-and-comments.md): whitespace and comments, nested data, readable error messages, streaming output and performance.
- [Resources](resources.md): talks and further reading.
