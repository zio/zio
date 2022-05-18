---
id: zio-cache
title:  "ZIO Cache"
---

ZIO Cache is a library that makes it easy to optimize the performance of our application by caching values.

Sometimes we may call or receive requests to do overlapping work. Assume we are writing a service that is going to handle all incoming requests. We don't want to handle duplicate requests. Using ZIO Cache we can make our application to be more **performant** by preventing duplicated works.

## Introduction

Some key features of ZIO Cache:

- **Compositionality** — If we want our applications to be **compositional**, different parts of our application may do overlapping work. ZIO Cache helps us to stay benefit from compositionality while using caching.

- **Unification of Synchronous and Asynchronous Caches** — Compositional definition of cache in terms of _lookup function_ unifies synchronous and asynchronous caches. So the lookup function can compute value either synchronously or asynchronously.

- **Deep ZIO Integration** — ZIO Cache is a ZIO native solution. So without losing the power of ZIO it includes support for _concurrent lookups_, _failure_, and _interruption_.

- **Caching Policy** — Using caching policy, the ZIO Cache can determine when values should/may be removed from the cache. So, if we want to build something more complex and custom we have a lot of flexibility. The caching policy has two parts and together they define a whole caching policy:

    - **Priority (Optional Removal)** — When we are running out of space, it defines the order that the existing values **might** be removed from the cache to make more space.

    - **Evict (Mandatory Removal)** — Regardless of space when we **must** remove existing values because they are no longer valid anymore. They might be invalid because they do not satisfy business requirements (e.g., maybe it's too old). This is a function that determines whether an entry is valid based on the entry and the current time.

- **Composition Caching Policy** — We can define much more complicated caching policies out of much simpler ones.

- **Cache/Entry Statistics** — ZIO Cache maintains some good statistic metrics, such as entries, memory size, hits, misses, loads, evictions, and total load time. So we can look at how our cache is doing and decide where we should change our caching policy to improve caching metrics.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-cache" % "0.2.0-RC6"
```

## Example

In this example, we are calling `timeConsumingEffect` three times in parallel with the same key. The ZIO Cache runs this effect only once. So the concurrent lookups will suspend until the value being computed is available:

```scala mdoc:compile-only
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
Result of parallel execution three effects with the same key: ((3288498,3288498),3288498)
Number of cache hits: 2
Number of cache misses: 1
```

## Resources

- [Compositional Caching](https://www.youtube.com/watch?v=iFeTUhYpPLs) by Adam Fraser (December 2020) — In this talk, Adam will introduce ZIO Cache, a new library in the ZIO ecosystem that provides a drop-in caching solution for ZIO applications. We will see how ZIO’s support for asynchrony and concurrent lets us implement a cache in terms of a single lookup function and how we get many other things such as typed errors and compositional caching policies for free. See how easy it can be to add caching to your ZIO application!
