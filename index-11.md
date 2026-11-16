# Introduction to ZIO Cache

> ZIO Cache is a library that makes it easy to optimize the performance of our application by caching values.

[![Development](https://img.shields.io/badge/Project%20Stage-Development-green.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-cache/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-cache_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-cache_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-cache_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-cache_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-cache_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-cache_2.13) [![ZIO Cache](https://img.shields.io/github/stars/zio/zio-cache?style=social)](https://github.com/zio/zio-cache)

## Introduction

Sometimes we may call or receive requests to do overlapping work. Assume we are writing a service that is going to handle all incoming requests. We don't want to handle duplicate requests. Using ZIO Cache we can make our application to be more **performant** by preventing duplicated works.

Some key features of ZIO Cache:

- **Compositionality** — If we want our applications to be **compositional**, different parts of our application may do overlapping work. ZIO Cache helps us to stay benefit from compositionality while using caching.
- **Unification of Synchronous and Asynchronous Caches** — Compositional definition of cache in terms of _lookup function_ unifies synchronous and asynchronous caches. So the lookup function can compute value either synchronously or asynchronously.

- **Deep ZIO Integration** — ZIO Cache is a ZIO native solution. So without losing the power of ZIO it includes support for _concurrent lookups_, _failure_, and _interruption_.
- **Capacity and Time to Live** — A cache holds at most a fixed number of values. When it is full, it removes the least recently accessed value. Each value also expires after a time to live, and that time can depend on whether the lookup succeeded or failed.

- **Cache/Entry Statistics** — ZIO Cache tracks hits, misses, and size for the whole cache, and the time each entry was loaded. So we can look at how our cache is doing and decide whether its capacity and time to live need to change.

## How to Define a Cache?

A cache is defined in terms of a lookup function that describes how to compute the value associated with a key if a value is not already in the cache.

```scala
import zio._
import zio.cache.Lookup

val lookup: Lookup[String, Any, Nothing, Int] =
  Lookup(key => ZIO.succeed(key.length))
```

The lookup function takes a key of type `Key` and returns a `ZIO` effect that requires an environment of type `Environment` and can fail with an error of type `Error` or succeed with a value of type `Value`. Because the lookup function returns a `ZIO` effect it can describe both synchronous and asynchronous workflows.

We construct a cache using a lookup function as well as a maximum size and a time to live.

```scala
import zio._
import zio.cache.{Cache, Lookup}

val cache: UIO[Cache[String, Nothing, Int]] =
  Cache.make(
    capacity = 100,
    timeToLive = Duration.Infinity,
    lookup = Lookup(key => ZIO.succeed(key.length))
  )
```

Once we have created a cache the most idiomatic way to work with it is the `get` operator. The `get` operator will return the current value in the cache if it exists or else compute a new value, put it in the cache, and return it.

If multiple concurrent processes get the value at the same time the value will only be computed once, with all of the other processes receiving the computed value as soon as it is available. All of this will be done using ZIO's fiber based concurrency model without ever blocking any underlying operating system threads.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```sbt
libraryDependencies += "dev.zio" %% "zio-cache" % "0.4.0"
```

## Example

In this example, we are calling `timeConsumingEffect` three times in parallel with the same key. The ZIO Cache runs this effect only once. So the concurrent lookups will suspend until the value being computed is available:

```scala
import zio._
import zio.cache.{Cache, Lookup}

object ZIOCacheExample extends ZIOAppDefault {
  def timeConsumingEffect(key: String) =
    ZIO.sleep(5.seconds).as(key.hashCode)

  def run =
    for {
      cache <- Cache.make(
        capacity = 100,
        timeToLive = Duration.Infinity,
        lookup = Lookup(timeConsumingEffect)
      )
      result <- cache
        .get("key1")
        .zipPar(cache.get("key1"))
        .zipPar(cache.get("key1"))
      _ <- ZIO.debug(
        s"Result of parallel execution of three effects with the same key: $result"
      )

      hits <- cache.cacheStats.map(_.hits)
      misses <- cache.cacheStats.map(_.misses)
      _ <- ZIO.debug(s"Number of cache hits: $hits")
      _ <- ZIO.debug(s"Number of cache misses: $misses")
    } yield ()

}
```

The output of this program should be as follows:

```
Result of parallel execution of three effects with the same key: ((3288498,3288498),3288498)
Number of cache hits: 2
Number of cache misses: 1
```

## Learn More

To build your first cache step by step, follow the [tutorial](./tutorial-first-cache.md). To make specific decisions, read the how-to guides, starting with [How to Choose Capacity and Time to Live](./how-to-choose-capacity-and-ttl.md). The reference pages describe [Cache](./cache.md), [ScopedCache](./scoped-cache.md), [Lookup](./lookup.md), and the statistics types.

## Resources

- [Compositional Caching](https://www.youtube.com/watch?v=iFeTUhYpPLs) by Adam Fraser (December 2020) — In this talk, Adam will introduce ZIO Cache, a new library in the ZIO ecosystem that provides a drop-in caching solution for ZIO applications. We will see how ZIO’s support for asynchrony and concurrent lets us implement a cache in terms of a single lookup function and how we get many other things such as typed errors and compositional caching policies for free. See how easy it can be to add caching to your ZIO application!
