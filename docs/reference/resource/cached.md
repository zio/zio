---
id: cached
title: "Cached: Automatic and Manual Resource Caching"
sidebar_label: Cached
description: "Cached provides automatic and manual caching of expensive resourceful values with scheduled refresh policies and graceful error handling."
keywords:
  - Cached
  - "Resource Caching"
  - "Automatic Refresh"
  - "Manual Refresh"
  - "Resource Management"
  - "Cache Invalidation"
  - "Schedule"
---

`Cached[Error, Resource]` is a container for a possibly resourceful value that is loaded into memory and can be refreshed either manually or automatically according to a schedule. It provides a way to cache expensive computations or resource acquisitions with configurable refresh policies.

## Motivation

In many applications, we need to periodically acquire expensive resources or compute costly values. However, doing this on-demand each time can be inefficient:

- **Expensive Resource Acquisition**: Connecting to a database, establishing a network connection, or fetching data from a remote service can be time-consuming.
- **Repetitive Computation**: Some values are expensive to compute and don't change frequently, so recomputing them repeatedly wastes CPU cycles.
- **Unpredictable Latency**: Without caching, every access to a resource introduces unpredictable latency.

The traditional approach is to cache values manually:
- Store the computed value in a mutable reference
- Invalidate it when it becomes stale
- Recompute only when needed

This approach has problems:
- Manual cache invalidation is error-prone
- Refresh timing must be managed explicitly
- Failed refreshes can invalidate previously working cached values
- Coordinating cache updates with resource acquisition is tricky

`Cached` solves these problems by:
- Providing two simple refresh strategies: **manual** (you control when to refresh) and **automatic** (scheduled by a `Schedule`)
- Ensuring failed refreshes don't invalidate previously successful cached values (graceful degradation)
- Properly managing the lifetime of cached resources using ZIO's resource system
- Supporting both simple values and complex resourceful effects

## Quick Showcase

Here are the two main ways to use `Cached`:

**Manual Refresh** — You control when to refresh:

```scala mdoc:reset:invisible
import zio._
```

Create a cache and manually trigger a refresh:

```scala mdoc:compile-only
import zio._

object ManualRefreshExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make(0)
      cached <- Cached.manual(ref.get)
      value1 <- cached.get
      _ <- ZIO.debug(s"First value: $value1")
      _ <- ref.set(42)
      _ <- cached.refresh
      value2 <- cached.get
      _ <- ZIO.debug(s"Second value: $value2")
    } yield ()
  }
}
```

**Automatic Refresh** — A schedule controls refresh timing:

```scala mdoc:compile-only
import zio._
import zio.test.TestClock

object AutoRefreshExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make(0)
      cached <- Cached.auto(ref.get, Schedule.spaced(10.seconds))
      value1 <- cached.get
      _ <- ZIO.debug(s"Current value: $value1")
      _ <- ref.set(99)
      _ <- TestClock.adjust(15.seconds)
      value2 <- cached.get
      _ <- ZIO.debug(s"Value after refresh: $value2")
    } yield ()
  }
}
```

## Construction

`Cached` is created using two companion object methods:

### `Cached.manual`

Creates a cache that must be manually refreshed by calling the `Cached#refresh` method.

**Signature:**
```scala
object Cached {
  def manual[R, Error, Resource](
    acquire: ZIO[R, Error, Resource]
  ): ZIO[R with Scope, Nothing, Cached[Error, Resource]]
}
```

**Parameters:**
- `acquire`: A `ZIO` effect that acquires or computes the resource. This effect is executed each time `refresh()` is called.

**Returns:** A scoped effect that produces a `Cached` value.

**Description:**
The `Cached.manual` constructor creates a cache that stores the result of the `acquire` effect. You must explicitly call `Cached#refresh()` to update the cached value. This is useful when:
- Refresh timing is event-driven (e.g., triggered by user action)
- You need fine-grained control over when refreshes happen
- The refresh pattern is irregular or unpredictable

To create a manual cache that you can refresh on demand:

```scala mdoc:compile-only
import zio._

object ManualCacheExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make("initial")
      cached <- Cached.manual(ref.get)
      v1 <- cached.get
      _ <- ZIO.debug(s"Value: $v1")
      _ <- ref.set("updated")
      _ <- cached.refresh
      v2 <- cached.get
      _ <- ZIO.debug(s"Value after refresh: $v2")
    } yield ()
  }
}
```

### `Cached.auto`

Creates a cache that is automatically refreshed according to a schedule policy.

**Signature:**
```scala
object Cached {
  def auto[R, Error, Resource](
    acquire: ZIO[R, Error, Resource], 
    policy: Schedule[Any, Any, Any]
  ): ZIO[R with Scope, Nothing, Cached[Error, Resource]]
}
```

**Parameters:**
- `acquire`: A `ZIO` effect that acquires or computes the resource.
- `policy`: A `Schedule` that determines when refreshes occur. The schedule is run in a daemon fiber spawned by the auto cache.

**Returns:** A scoped effect that produces a `Cached` value.

**Description:**
The `Cached.auto` constructor creates a cache that refreshes automatically according to the provided schedule. This is useful when:
- Refresh timing is regular and predictable (e.g., every N seconds)
- You want the cache to stay fresh without manual intervention
- The resource should be periodically reloaded (e.g., configuration, slowly-changing data)

**Important Notes:**
- Error retrying is NOT performed automatically. If you need retry logic, apply retry policies to the `acquire` effect before passing it to `auto()`.
- Failed refreshes do NOT invalidate the cached value, allowing graceful degradation when refresh attempts fail temporarily.
- The refresh is executed in a daemon fiber that runs until the scope is closed.

To create an automatic cache that refreshes on a fixed schedule:

```scala mdoc:compile-only
import zio._
import zio.test.TestClock

object AutoCacheExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make(java.lang.System.currentTimeMillis())
      cached <- Cached.auto(
        ref.get.flatMap(ts => ZIO.succeed(s"Timestamp: $ts")),
        Schedule.spaced(5.seconds)
      )
      value1 <- cached.get
      _ <- ZIO.debug(value1)
      _ <- TestClock.adjust(6.seconds)
      value2 <- cached.get
      _ <- ZIO.debug(value2)
    } yield ()
  }
}
```

### Auto Cache with Retry Policy

If you need automatic refresh with error recovery, apply retry policies to the `acquire` effect:

```scala mdoc:compile-only
import zio._

object AutoCacheWithRetryExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make[Either[String, Int]](Right(42))
      acquire = ref.get.absolve.retry(Schedule.recurs(3))
      cached <- Cached.auto(acquire, Schedule.spaced(5.seconds))
      value1 <- cached.get
      _ <- ZIO.debug(s"Value: $value1")
    } yield ()
  }
}
```

## Core Operations

The core operations of `Cached` provide simple methods to retrieve and update cached values.

### `Cached#get`

Retrieves the currently cached value.

**Signature:**
```scala
trait Cached[Error, Resource] {
  def get: IO[Error, Resource]
}
```

**Returns:** An effect that produces the cached resource, or fails with an error if the initial acquisition failed.

**Description:**
The `Cached#get` method returns the most recently cached value. If the cache has never been successfully populated (either because `Cached.manual` hasn't been refreshed yet, or because `Cached.auto` hasn't completed its first refresh), the returned effect fails. After at least one successful refresh, `Cached#get` always returns that cached value immediately, even if subsequent refreshes fail.

**Behavior:**
- Returns the last successfully cached value
- Fails if the cache was never successfully populated
- Does not trigger a refresh (see `Cached#refresh` for that)
- Returns immediately without blocking

To retrieve the currently cached value after it has been populated:

```scala mdoc:compile-only
import zio._

object GetExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make(100)
      cached <- Cached.manual(ref.get)
      _ <- cached.refresh
      value <- cached.get
      _ <- ZIO.debug(s"Cached value: $value")
    } yield ()
  }
}
```

### `Cached#refresh`

Manually refreshes the cached value by re-executing the `acquire` effect.

**Signature:**
```scala
trait Cached[Error, Resource] {
  def refresh: IO[Error, Unit]
}
```

**Returns:** An effect that completes when the refresh is done, or fails if the refresh fails.

**Description:**
The `Cached#refresh` method explicitly triggers a cache refresh by re-running the `acquire` effect. It blocks until the refresh completes. Unlike automatic refresh, refresh errors prevent the cache from being updated while allowing the previously cached value to remain.

**Behavior:**
- Re-executes the `acquire` effect
- Updates the cache on success
- Leaves the cache unchanged on failure
- Blocks until the refresh completes (uninterruptible during the actual acquisition)
- In `auto` caches, refreshes also happen automatically according to the schedule

To manually refresh a cache and retrieve the updated value:

```scala mdoc:compile-only
import zio._

object RefreshExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make("data-v1")
      cached <- Cached.manual(ref.get)
      _ <- cached.refresh
      v1 <- cached.get
      _ <- ZIO.debug(s"First version: $v1")
      _ <- ref.set("data-v2")
      _ <- cached.refresh
      v2 <- cached.get
      _ <- ZIO.debug(s"Second version: $v2")
    } yield ()
  }
}
```

## Graceful Error Handling

A key feature of `Cached` is that failed refreshes don't invalidate previously successful cached values. This provides graceful degradation when refresh operations temporarily fail.

To observe how a cache maintains the previous value when a refresh fails:

```scala mdoc:compile-only
import zio._
import zio.test.TestClock

object GracefulDegradationExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make[Either[String, String]](Right("initial-data"))
      acquire = ref.get.absolve
      cached <- Cached.auto(acquire, Schedule.spaced(1.second))
      _ <- cached.refresh
      v1 <- cached.get
      _ <- ZIO.debug(s"Initial value: $v1")
      _ <- ref.set(Left("error"))
      _ <- TestClock.adjust(2.seconds)
      v2 <- cached.get
      _ <- ZIO.debug(s"Value despite refresh failure: $v2")
    } yield ()
  }
}
```

In this example, even though the refresh fails (because the ref contains a `Left`), the `Cached#get` still returns `"initial-data"`. The previous successful value is preserved, allowing the application to continue functioning with stale data rather than crashing.

## Scoped Resource Management

`Cached` uses ZIO's resource system to manage its lifetime. Both `Cached.manual` and `Cached.auto` return a scoped effect:

```scala mdoc:compile-only
import zio._

object ScopedCacheExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      cached <- Cached.manual(ZIO.succeed(42))
      _ <- cached.refresh
      value <- cached.get
      _ <- ZIO.debug(value)
    } yield ()
  }
}
```

The `ZIO.scoped` combinator creates a `Scope`, passes it to the cache creation, and ensures cleanup when the scope exits. For `auto` caches, this ensures the daemon fiber that manages automatic refresh is properly terminated.

## Integration with Schedule

The `Schedule` type provides powerful timing policies for `auto` caches. Here are some common patterns:

To set up a cache that refreshes at a fixed interval:

```scala mdoc:compile-only
import zio._

val acquire = ZIO.succeed(42)
val cache = Cached.auto(acquire, Schedule.spaced(5.seconds))
```

To refresh the cache after a fixed delay between successive refreshes:

```scala mdoc:compile-only
import zio._

val acquire = ZIO.succeed(42)
val cache = Cached.auto(acquire, Schedule.fixed(5.seconds))
```

To limit automatic refreshes to a maximum number of times:

```scala mdoc:compile-only
import zio._

val acquire = ZIO.succeed(42)
val cache = Cached.auto(acquire, Schedule.recurs(10))
```

**Exponential Backoff (for retrying):**
Apply retry to the acquire effect:
```scala mdoc:compile-only
import zio._

val acquire: ZIO[Any, String, Int] = ZIO.succeed(42)
val acquireWithRetry = acquire.retry(Schedule.exponential(100.millis))
val cache = Cached.auto(acquireWithRetry, Schedule.spaced(5.seconds))
```

## Common Patterns

Here are some practical examples of using `Cached` for common scenarios:

### Caching with Resource Acquisition

For resources that require explicit cleanup:

```scala mdoc:compile-only
import zio._

object ResourceCacheExample extends ZIOAppDefault {
  case class Connection(id: String)
  
  def acquireConnection: ZIO[Scope, Nothing, Connection] =
    ZIO.acquireRelease(
      ZIO.debug("Acquiring connection").as(Connection("db-1"))
    )(_ => ZIO.debug("Closing connection"))
  
  def run = ZIO.scoped {
    for {
      cached <- Cached.auto(acquireConnection, Schedule.spaced(30.seconds))
      _ <- cached.refresh
      conn <- cached.get
      _ <- ZIO.debug(s"Using connection: ${conn.id}")
    } yield ()
  }
}
```

### Caching with Fallback

Combine `Cached` with error handling for resilient systems:

```scala mdoc:compile-only
import zio._

object CacheWithFallbackExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make[Either[String, Int]](Right(1))
      cached <- Cached.auto(ref.get.absolve, Schedule.spaced(5.seconds))
      _ <- cached.refresh
      value <- cached.get.catchAll(_ => ZIO.succeed(0))
      _ <- ZIO.debug(s"Value (with fallback): $value")
    } yield ()
  }
}
```

### Manual Cache for Event-Driven Refresh

When refreshes are triggered by external events:

```scala mdoc:compile-only
import zio._

object EventDrivenCacheExample extends ZIOAppDefault {
  def run = ZIO.scoped {
    for {
      ref <- Ref.make("config-v1")
      cached <- Cached.manual(ref.get)
      _ <- cached.refresh
      v1 <- cached.get
      _ <- ZIO.debug(s"Initial config: $v1")
      _ <- ref.set("config-v2")
      _ <- cached.refresh
      v2 <- cached.get
      _ <- ZIO.debug(s"Updated config: $v2")
    } yield ()
  }
}
```

## Comparison with ZIO.cached()

ZIO provides a time-based caching method directly on effects:

```scala
def cached(timeToLive: => Duration): ZIO[R, Nothing, IO[E, A]]
def cachedInvalidate(timeToLive: => Duration): ZIO[R, Nothing, (IO[E, A], UIO[Unit])]
```

**Differences from `Cached`:**

| Feature | `Cached` | `ZIO#cached()` |
|---------|----------|---|
| Refresh Strategy | Manual or Scheduled | Time-based expiration only |
| Graceful Degradation | Yes (failed refresh keeps old value) | No (expired cache re-runs effect) |
| Resource Management | Explicit scope | Implicit Promise-based |
| Refresh Control | Explicit `refresh()` method | Implicit on timeout |
| Error Retrying | Must apply to acquire | N/A (re-runs on expiration) |

Use `Cached` when you need:
- Scheduled (not just time-based) refresh patterns
- Graceful degradation on refresh failures
- Explicit refresh control
- Fine-grained resource lifecycle management

Use `ZIO#cached()` when you need:
- Simple time-based caching without complexity
- Automatic cache invalidation after a duration
- Minimal overhead and configuration
