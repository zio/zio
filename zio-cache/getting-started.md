# Getting Started with ZIO Cache

> This page shows how to define a cache and read a value from it. To add ZIO Cache to your build, see [Installation](./index.md#installation).

## Defining a Cache

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

## Example: Concurrent Lookups

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

## Where to Go Next

To build the same cache step by step, follow the [tutorial](./tutorial-first-cache.md).
