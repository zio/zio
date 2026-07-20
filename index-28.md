# Introduction to ZIO Quill

> Quill provides a Quoted Domain Specific Language ([QDSL](https://homepages.inf.ed.ac.uk/wadler/papers/qdsl/qdsl.pdf)) to express queries in Scala and execute them in a target language.

Quill provides a Quoted Domain Specific Language ([QDSL](https://homepages.inf.ed.ac.uk/wadler/papers/qdsl/qdsl.pdf)) to express queries in Scala and execute them in a target language. 

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-quill/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/io.getquill/quill-core_2.12.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/io/getquill/quill-core_2.12/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/io.getquill/quill-core_2.12.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/io/getquill/quill-core_2.12/) [![javadoc](https://javadoc.io/badge2/io.getquill/zio-quill-docs_2.12/javadoc.svg)](https://javadoc.io/doc/io.getquill/zio-quill-docs_2.12) [![ZIO Quill](https://img.shields.io/github/stars/zio/zio-quill?style=social)](https://github.com/zio/zio-quill)

## Introduction

The library's core is designed to support multiple target languages, currently featuring specializations for Structured Query Language ([SQL](https://en.wikipedia.org/wiki/SQL)) and Cassandra Query Language ([CQL](https://cassandra.apache.org/doc/latest/cql/)).

1. **Boilerplate-free mapping**: The database schema is mapped using simple case classes.
2. **Quoted DSL**: Queries are defined inside a `quote` block. Quill parses each quoted block of code (quotation) at compile time and translates them to an internal Abstract Syntax Tree (AST)
3. **Compile-time query generation**: The `ctx.run` call reads the quotation's AST and translates it to the target language at compile time, emitting the query string as a compilation message. As the query string is known at compile time, the runtime overhead is very low and similar to using the database driver directly.
4. **Compile-time query validation**: If configured, the query is verified against the database at compile time and the compilation fails if it is not valid. The query validation **does not** alter the database state.

> ### Scala 3 Support
> [ProtoQuill](https://github.com/zio/zio-protoquill) provides Scala 3 support for Quill rebuilding on top of new metaprogramming capabilities from the ground > up! It is published to maven-central as the `quill-<module>_3` line of artifacts.

> ### Doobie Support
> See [here](contexts.md#quill-doobie) for Doobie integration instructions.

## Example

![example](https://raw.githubusercontent.com/getquill/quill/master/example.gif)

Note: The GIF example uses Eclipse, which shows compilation messages to the user.
