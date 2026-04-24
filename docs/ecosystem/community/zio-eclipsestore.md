---
id: zio-eclipsestore
title: "ZIO EclipseStore"
---

[ZIO EclipseStore](https://github.com/riccardomerolla/zio-eclipsestore) is a ZIO-based library for type-safe, efficient, and boilerplate-free access to [EclipseStore](https://github.com/eclipse-store/store).

## Introduction

ZIO EclipseStore provides a ZIO-powered interface to EclipseStore, offering a modern, functional approach to object graph persistence with type safety and composability.

## Features

- **Type-Safe API** — Leverage ZIO's type system for compile-time safety
- **Automatic Query Batching** — Batchable queries are optimized automatically
- **Parallel Execution** — Un-batchable queries execute in parallel with controlled concurrency
- **Typed Root Instances** — Declaratively describe and access root aggregates
- **Lifecycle Management** — Checkpoints, backups, and restarts via `LifecycleCommand`
- **Streaming Persistence** — Stream keys/values and batch updates with `putAll`/`persistAll`
- **GigaMap Module** — Advanced indexed maps with query DSL, CRUD, and persistence support
- **Resource Safety** — ZIO's resource management ensures proper cleanup
- **Custom Type Handlers & Blobs** — Register custom binary handlers for domain types and blobs
- **Backup Targets & Import/Export** — Built-in backup configuration (SQLite/SQL/S3/FTP), import/export helpers
- **Performance Tuning** — Configure channels, caches, compression, off-heap page store, encryption
- **Lazy Loading & Eager Storing** — Lazy references/collections plus eager-field semantics
- **ZIO Config Integration** — Load `EclipseStoreConfig` from HOCON/resources via zio-config
- **Effect-Oriented** — All operations are ZIO effects for composability

## Installation

```scala
libraryDependencies ++= Seq(
  "io.github.riccardomerolla" %% "zio-eclipsestore" % "1.0.5",
  "io.github.riccardomerolla" %% "zio-eclipsestore-gigamap" % "1.0.5", // Optional: for GigaMap module
  "io.github.riccardomerolla" %% "zio-eclipsestore-storage-sqlite" % "1.0.5" // Optional: for SQLite storage/backup
)
```

## Example

A simple demo showcasing batch operations, typed roots, and query execution:

```scala
import io.github.riccardomerolla.zio.eclipsestore.config.EclipseStoreConfig
import io.github.riccardomerolla.zio.eclipsestore.domain.{Query, RootDescriptor}
import io.github.riccardomerolla.zio.eclipsestore.service.{EclipseStoreService, LifecycleCommand}
import zio.*
import scala.collection.mutable.ListBuffer

object MyApp extends ZIOAppDefault:
  def run =
    val program = for
      // Batch store values
      _ <- EclipseStoreService.putAll(
        List("user:1" -> "Alice", "user:2" -> "Bob", "user:3" -> "Charlie")
      )
      
      // Retrieve values
      user <- EclipseStoreService.get[String, String]("user:1")
      _ <- ZIO.logInfo(s"User: ${user.getOrElse("not found")}")
      
      // Stream all values
      streamed <- EclipseStoreService.streamValues[String].runCollect
      _ <- ZIO.logInfo(s"All users: ${streamed.mkString(", ")}")
      
      // Work with typed roots
      favoritesDescriptor = RootDescriptor(
        id = "favorite-users",
        initializer = () => ListBuffer.empty[String]
      )
      favorites <- EclipseStoreService.root(favoritesDescriptor)
      _ <- ZIO.succeed(favorites.addOne("user:1"))
      _ <- EclipseStoreService.maintenance(LifecycleCommand.Checkpoint)
      
      // Execute multiple queries in batch
      queries = List(
        Query.Get[String, String]("user:1"),
        Query.Get[String, String]("user:2"),
        Query.Get[String, String]("user:3")
      )
      results <- EclipseStoreService.executeMany(queries)
      _ <- ZIO.logInfo(s"Batch query results: ${results.mkString(", ")}")
    yield ()
    
    program.provide(
      EclipseStoreConfig.temporaryLayer,
      EclipseStoreService.live
    )
```

See the full docs on the project's [GitHub repository](https://github.com/riccardomerolla/zio-eclipsestore).