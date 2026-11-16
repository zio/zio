# Tutorial: Your First Cache

> Build a small cache around a slow lookup, then read values, statistics, and concurrent results with ZIO Cache.

Welcome to the ZIO Cache tutorial! This tutorial is for newcomers to ZIO who want to avoid repeating slow work. You need to know basic Scala syntax. You don't need any prior experience with caching or with ZIO Cache to follow along.

## Learning Objectives

By the end of this tutorial, you will understand:

- What a lookup function is, and how it describes the work a cache avoids repeating
- How to create a cache with `Cache.make`, including its capacity and time to live
- How `Cache#get` returns a stored value instead of computing it again
- How concurrent calls for the same key share one computation

We'll learn these concepts through:

1. A Slow Lookup
2. Creating a Cache
3. Getting a Value
4. Reading the Statistics
5. Concurrent Lookups Share One Computation
6. Invalidating a Value

We recommend reading from top to bottom, because each section builds on the previous one.

## 1. A Slow Lookup

Imagine a service that loads a user profile from a remote system. The call takes a long time, and the same user may be requested many times. The function below stands in for that remote call. It sleeps for 100 milliseconds to simulate the delay:

```scala
import zio._

def loadProfile(userId: String): IO[String, String] =
  if (userId.isEmpty) ZIO.fail("user id must not be empty")
  else ZIO.sleep(100.millis).as(s"profile of $userId")
```

The function takes a `String` key and returns a `ZIO` effect:

- `IO[String, String]` means the effect can fail with a `String` error or succeed with a `String` value.
- `ZIO.sleep(100.millis)` simulates the slow remote call.
- `.as(...)` replaces the sleep's result with the profile text.

A function like this is what a cache needs. A cache does not know how to load a value by itself. It asks a lookup function for the value it does not have yet.

## 2. Creating a Cache

ZIO Cache wraps a lookup function in a `Lookup`. We pass the `Lookup`, a capacity, and a time to live to `Cache.make`:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, String] =
  if (userId.isEmpty) ZIO.fail("user id must not be empty")
  else ZIO.sleep(100.millis).as(s"profile of $userId")

val cache: UIO[Cache[String, String, String]] =
  Cache.make(
    capacity = 10,
    timeToLive = 1.minute,
    lookup = Lookup(loadProfile)
  )
```

- `Lookup(loadProfile)` turns our function into a lookup function.
- `capacity = 10` lets the cache hold at most ten entries. When it is full, it evicts the least recently accessed one.
- `timeToLive = 1.minute` means a stored value expires once it is more than one minute old.
- `Cache.make` returns a `UIO` effect. Creating a cache cannot fail, so its error type is `Nothing`.

:::note
The cache's type is `Cache[String, String, String]`: the key type, the error type, and the value type. The environment type of the lookup is gone because `loadProfile` needs no environment.
:::

## 3. Getting a Value

`Cache#get` is the main operation. It returns the stored value when there is one. Otherwise it runs the lookup, stores the result, and returns it. We call `Cache#get` twice with the same key to see the difference:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, String] =
  if (userId.isEmpty) ZIO.fail("user id must not be empty")
  else ZIO.sleep(100.millis).as(s"profile of $userId")

val firstAndSecond: IO[String, (String, String)] =
  for {
    cache  <- Cache.make(capacity = 10, timeToLive = 1.minute, lookup = Lookup(loadProfile))
    first  <- cache.get("alice") // runs loadProfile and stores the result
    second <- cache.get("alice") // returns the stored result without running loadProfile
  } yield (first, second)
```

The first call to `Cache#get` is slow because the lookup runs. The second call returns right away. Both calls return the same `"profile of alice"` value.

:::tip
Keep the cache in a `val` or a service and share it. A cache built inside every request starts empty each time, so it never gets a hit.
:::

## 4. Reading the Statistics

The cache counts hits and misses for us. A hit means a `Cache#get` call found a stored value. A miss means a `Cache#get` call ran the lookup. `Cache#cacheStats` reports these counts together with the current size:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, String] =
  if (userId.isEmpty) ZIO.fail("user id must not be empty")
  else ZIO.sleep(100.millis).as(s"profile of $userId")

val statistics: IO[String, (Long, Long, Int)] =
  for {
    cache <- Cache.make(capacity = 10, timeToLive = 1.minute, lookup = Lookup(loadProfile))
    _     <- cache.get("alice")
    _     <- cache.get("alice")
    stats <- cache.cacheStats
  } yield (stats.hits, stats.misses, stats.size)
```

After the two calls above, the cache has one miss (the first call), one hit (the second call), and one stored value. The `statistics` value is `(1, 1, 1)`.

## 5. Concurrent Lookups Share One Computation

Several fibers may ask for the same key at the same time. ZIO Cache runs the lookup only once and gives the result to all of them. We start three concurrent calls for `"bob"`:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, String] =
  if (userId.isEmpty) ZIO.fail("user id must not be empty")
  else ZIO.sleep(100.millis).as(s"profile of $userId")

val concurrent: IO[String, (List[String], Long, Long)] =
  for {
    cache   <- Cache.make(capacity = 10, timeToLive = 1.minute, lookup = Lookup(loadProfile))
    results <- ZIO.foreachPar(List("bob", "bob", "bob"))(cache.get)
    stats   <- cache.cacheStats
  } yield (results, stats.hits, stats.misses)
```

All three calls return `"profile of bob"`. Only one of them is a miss, because the lookup ran once. The other two are hits, because they wait for the value that is already being computed. The result is `(List("profile of bob", "profile of bob", "profile of bob"), 2, 1)`.

## 6. Invalidating a Value

Sometimes we know a stored value is out of date. `Cache#invalidate` removes the value for one key, and `Cache#contains` tells us whether a value is stored for a key:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, String] =
  if (userId.isEmpty) ZIO.fail("user id must not be empty")
  else ZIO.sleep(100.millis).as(s"profile of $userId")

val invalidation: IO[String, Boolean] =
  for {
    cache <- Cache.make(capacity = 10, timeToLive = 1.minute, lookup = Lookup(loadProfile))
    _     <- cache.get("alice")
    _     <- cache.invalidate("alice")
    has   <- cache.contains("alice")
  } yield has
```

The `invalidation` value is `false`. The next `Cache#get` call for `"alice"` runs the lookup again.

## Putting It Together

This program combines the steps above into one runnable `ZIOAppDefault`. It creates one cache, calls `Cache#get` for a single key, reads the statistics, runs concurrent lookups, invalidates a key, and shows what a failed lookup returns:

```scala
import zio._
import zio.cache.{Cache, Lookup}

object FirstCacheTutorial extends ZIOAppDefault {

  def loadProfile(userId: String): IO[String, String] =
    if (userId.isEmpty) ZIO.fail("user id must not be empty")
    else ZIO.sleep(100.millis).as(s"profile of $userId")

  def run =
    for {
      cache   <- Cache.make(capacity = 10, timeToLive = 1.minute, lookup = Lookup(loadProfile))
      first   <- cache.get("alice")
      _       <- ZIO.debug(s"first get: $first")
      second  <- cache.get("alice")
      _       <- ZIO.debug(s"second get: $second")
      stats   <- cache.cacheStats
      _       <- ZIO.debug(s"after alice: hits=${stats.hits} misses=${stats.misses} size=${stats.size}")
      results <- ZIO.foreachPar(List("bob", "bob", "bob"))(cache.get)
      _       <- ZIO.debug(s"concurrent results: $results")
      stats2  <- cache.cacheStats
      _       <- ZIO.debug(s"after bob: hits=${stats2.hits} misses=${stats2.misses} size=${stats2.size}")
      _       <- cache.invalidate("alice")
      has     <- cache.contains("alice")
      _       <- ZIO.debug(s"contains alice after invalidate: $has")
      failed  <- cache.get("").either
      _       <- ZIO.debug(s"empty id: $failed")
    } yield ()
}
```

Running this program prints the following lines:

```text
first get: profile of alice
second get: profile of alice
after alice: hits=1 misses=1 size=1
concurrent results: List(profile of bob, profile of bob, profile of bob)
after bob: hits=3 misses=2 size=2
contains alice after invalidate: false
empty id: Left(user id must not be empty)
```

## What You've Learned

In this tutorial, you learned:

- What a lookup function is, and how `Lookup` wraps it for a cache
- How to create a cache with `Cache.make`, choosing its capacity and time to live
- How `Cache#get` returns a stored value, and runs the lookup only when no value is stored
- How hits and misses appear in `Cache#cacheStats`
- How concurrent calls for the same key share one computation
- How `Cache#invalidate` and `Cache#contains` remove and check stored values

You now have a solid foundation for caching with ZIO Cache. The next step is to make the choices a real service needs, such as the right capacity and time to live.

## Where to Go Next

- **Ready to use this in practice?** Read [How to Choose Capacity and Time to Live](./how-to-choose-capacity-and-ttl.md) for guidance on sizing a cache.
- **Need a different expiry for each value?** Read [How to Set a Time to Live per Entry](./how-to-per-entry-ttl.md).
- **Want to dive deeper into the API?** Read the reference pages for [Cache](./cache.md), [Lookup](./lookup.md), and [Cache Statistics](./cache-statistics.md).
- **Caching resources that must be closed?** Read [Scoped Cache](./scoped-cache.md).
