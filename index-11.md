# Introduction to ZIO Cache

> ZIO Cache is a library that makes it easy to optimize the performance of our application by caching values.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-cache/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-cache_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-cache_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-cache_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-cache_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-cache_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-cache_2.13) [![ZIO Cache](https://img.shields.io/github/stars/zio/zio-cache?style=social)](https://github.com/zio/zio-cache)

## Introduction

Sometimes we may call or receive requests to do overlapping work. Assume we are writing a service that is going to handle all incoming requests. We don't want to handle duplicate requests. Using ZIO Cache we can make our application more **performant** by preventing duplicated work.

Some key features of ZIO Cache:

- **Compositionality** — If we want our applications to be **compositional**, different parts of our application may do overlapping work. ZIO Cache lets us keep that compositionality while using caching.
- **Unification of Synchronous and Asynchronous Caches** — Compositional definition of cache in terms of _lookup function_ unifies synchronous and asynchronous caches. So the lookup function can compute value either synchronously or asynchronously.

- **Deep ZIO Integration** — ZIO Cache is a ZIO native solution. So without losing the power of ZIO it includes support for _concurrent lookups_, _failure_, and _interruption_.
- **Capacity and Time to Live** — A cache holds at most a fixed number of values. When it is full, it removes the least recently accessed value. Each value also expires after a time to live, and that time can depend on whether the lookup succeeded or failed.

- **Cache/Entry Statistics** — ZIO Cache tracks hits, misses, and size for the whole cache, and the time each entry was loaded. So we can look at how our cache is doing and decide whether its capacity and time to live need to change.

## Installation

ZIO Cache is published for Scala 2.12, 2.13 and 3. Add the artifact that matches your platform to your `build.sbt`.

On the JVM:

```scala
libraryDependencies ++= Seq(
  // Cache, ScopedCache, Lookup and statistics (JVM)
  "dev.zio" %% "zio-cache" % "0.4.1"
)
```

On Scala.js or Scala Native, use `%%%` instead:

```scala
libraryDependencies ++= Seq(
  // Cache, ScopedCache, Lookup and statistics (Scala.js and Scala Native)
  "dev.zio" %%% "zio-cache" % "0.4.1"
)
```

## Learn More

Start with [Getting Started](./getting-started.md), then build your first cache step by step in the [tutorial](./tutorial-first-cache.md). To make specific decisions, read the how-to guides, starting with [How to Choose Capacity and Time to Live](./how-to-choose-capacity-and-ttl.md). The reference pages describe [Cache](./cache.md), [ScopedCache](./scoped-cache.md), [Lookup](./lookup.md), and the statistics types. Talks and articles are listed under [Resources](./resources.md).
