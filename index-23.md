# Introduction to ZIO Parser

> Library for constructing parsers and pretty printers based on invertible syntax descriptions

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-parser/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-parser_3.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-parser_3) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-parser_3%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-parser_3/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-parser_3/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-parser_3) [![ZIO Parser](https://img.shields.io/github/stars/zio/zio-parser?style=social)](https://github.com/zio/zio-parser)

## Introduction

[![Zymposium - ZIO Parser](https://i.ytimg.com/vi/DEPpL9LBiyA/maxresdefault.jpg)](https://www.youtube.com/watch?v=DEPpL9LBiyA)

## Installation

Start by adding `zio-parser` as a dependency to your project:
  
```scala
libraryDependencies += "dev.zio" %% "zio-parser" % "0.1.12"
```

## Getting Started

```scala
import zio.parser.*
```

Declare your parsing syntax:

```scala
val digitSyntax: Syntax[String, Char, Char, Char] = Syntax.digit
```

Parse your string:

```scala
val result: Either[StringParserError[String], Char] = digitSyntax.parseString("1")
// result: Either[StringParserError[String], Char] = Right('1')
```

Pretty print the parsing errors:

```scala
println(digitSyntax.parseString("Hello").left.map(_.pretty).merge)
// Hello
// ^
// error: Failure at position 0: not a digit
//
```

## Where to Go Next

- [Building Syntaxes](syntax.md): primitives, sequencing, alternatives, repetition and mapping.
- [Regex](regex.md): matching whole tokens efficiently.
- [Printing and Invertible Syntaxes](printing.md): printing values back with the same syntax.
- [Error Handling](errors.md): error types, naming syntaxes and pretty printing errors.
- [Example: a JSON Syntax](example-json.md): a complete parser and printer.
