# Cache

> A cache is defined in terms of a lookup function, a capacity, and a time to live.

```scala
import zio._
import zio.cache.{Cache, Lookup}

val cache: UIO[Cache[String, Nothing, Int]] =
  Cache.make(
    capacity = 100,
    timeToLive = 10.minutes,
    lookup = Lookup(key => ZIO.succeed(key.length))
  )
```

The most fundamental operator on a cache is `get`, which either returns the value in the cache if it exists or else computes a new value with the lookup function, puts it in the cache, and returns it.

## Concurrent Access

The cache is guaranteed to be safe for concurrent access. In addition, getting a value from the cache is efficient under concurrency.

If two concurrent processes attempt to get the same value and it does not exist in the cache, the value will be computed once and provided to both concurrent processes as soon as it is available. The concurrent processes will semantically block for the value to become available but no underlying operating system threads will block as a result of this operation.

In the event that the computation of the lookup function fails or is interrupted that will automatically be propagated to any concurrent processes waiting for the same value.

Failures will be cached and made available to subsequent calls to `get` to avoid repeatedly computing the same failed value. In the event of interruption the key will be removed from the cache so subsequent calls to `get` will attempt to compute the value again.

## Capacity

A cache is constructed with a specified capacity. When the cache is at capacity the least recently accessed values will be removed first.

Note that the size of the cache may slightly exceed the specified capacity between operations.

## Time To Live (TTL)

A cache also allows specifying a time to live. The cache guarantees that no value will be returned from the cache if its age is greater than the specified time to live.

The expiry time is set when the lookup completes: the time to live is added to that moment. A value whose expiry time has passed is treated as absent, so the next `get` computes it again.

The `Cache.make` constructor applies the same time to live to every value. To choose the time to live from the result of the lookup function, use `Cache.makeWith`. Its time to live function receives the `Exit` of the lookup, so a failure can expire sooner than a success:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, Int] =
  if (userId.isEmpty) ZIO.fail("Empty user id") else ZIO.succeed(userId.length)

val profiles: UIO[Cache[String, String, Int]] =
  Cache.makeWith(capacity = 100, lookup = Lookup(loadProfile))(exit =>
    if (exit.isSuccess) 10.minutes else 5.seconds
  )
```

## Custom Keys

By default, the key of a cache entry is the input of the lookup function. The `Cache.makeWithKey` constructor accepts a `keyBy` function that converts each input into the key stored in the cache. Use it when the input is large and only a part of it identifies the value:

```scala
import zio._
import zio.cache.{Cache, Lookup}

final case class Request(id: String, payload: Array[Byte])

def process(request: Request): IO[Nothing, Int] =
  ZIO.succeed(request.payload.length)

val requests: UIO[Cache[Request, Nothing, Int]] =
  Cache.makeWithKey(capacity = 100, lookup = Lookup(process))(
    timeToLive = _ => 1.minute,
    keyBy = (request: Request) => request.id
  )
```

The `keyBy` function only changes the key that the cache stores. The lookup function still receives the full input, so two inputs with the same key share the cached value.

## Operators

In addition to `get`, `Cache` provides a variety of other operators.

```scala
import zio._
import zio.cache.Cache

def operators(cache: Cache[String, Nothing, Int]): UIO[Unit] =
  for {
    stats   <- cache.cacheStats
    entry   <- cache.entryStats("key")
    present <- cache.contains("key")
    count   <- cache.size
    _       <- ZIO.debug(s"$stats $entry $present $count")
    _       <- cache.refresh("key")
    _       <- cache.invalidate("key")
    _       <- cache.invalidateAll
  } yield ()
```

The `refresh` operator is similar to `get`. The difference is `refresh` triggers a re-computation of the value without invalidating it. This allows any request to the associated key to be served while the value is being re-computed / retrieved by the lookup function. Note: `refresh` always triggers the lookup function, disregarding the last `Error`. 

The `size` operator returns the current size of the cache. Under concurrent access the size should be regarded as only approximate since by the time we observe a given size the cache may have a different size based on concurrent insertions or removals.

Similarly, the `contains` operator returns whether a value associated with the specified key exists in the cache. An expired value does not count, so `contains` returns `false` for it even before it is removed. Again, under concurrent access `contains` is guaranteed to return whether the cache contains a value associated with the specified key as of a given point in time but that value may be concurrently added or removed immediately after that.

There are also the `cacheStats` and `entryStats` operators which allow obtaining a snapshot of statistics either for the cache itself or for a specified entry. See the sections on cache statistics and entry statistics for further discussion of this functionality.

The `invalidate` and `invalidateAll` operators can be used to evict a value associated with a specified key and evict all values, respectively.
