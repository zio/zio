# Scoped Cache

> A `ScopedCache` is a [cache](./cache.md) whose values are resources, such as database connections, file handles, or temporary directories. Each value must be released when the cache no longer needs it. A `ScopedCache` keeps a resource open while the cache holds it or while any caller still uses it, and releases the resource after it is evicted, invalidated, or expired and no caller uses it anymore.

If the cached values need no cleanup, use a plain [Cache](./cache.md) instead.

## Defining a Scoped Lookup

A `ScopedLookup` describes how to compute a value of type `Value` for a key of type `Key`. Unlike a [Lookup](./lookup.md), the lookup function requires a `Scope`. Any resource it acquires with `ZIO.acquireRelease` stays open for as long as the cached value lives:

```scala
import zio._
import zio.cache.ScopedLookup

final case class Connection(url: String)

val openConnection: ScopedLookup[String, Scope, Nothing, Connection] =
  ScopedLookup { (url: String) =>
    ZIO.acquireRelease(ZIO.succeed(Connection(url)))(connection =>
      ZIO.debug(s"Closing connection to ${connection.url}")
    )
  }
```

The `ScopedLookup` constructor takes a function from a key to a `ZIO` effect that requires `Scope`. Calling `ScopedLookup#apply` with a key returns that effect for the key.

## Creating a Scoped Cache

`ScopedCache.make` constructs a cache from a capacity, a time to live, and a `ScopedLookup`. The effect requires a `Scope`, which ties the lifetime of the cache to the enclosing scope. Closing that scope invalidates all values in the cache and releases the resources they hold:

```scala
import zio._
import zio.cache.{ScopedCache, ScopedLookup}

final case class Connection(url: String)

val openConnection: ScopedLookup[String, Scope, Nothing, Connection] =
  ScopedLookup { (url: String) =>
    ZIO.acquireRelease(ZIO.succeed(Connection(url)))(connection =>
      ZIO.debug(s"Closing connection to ${connection.url}")
    )
  }

val connections: ZIO[Scope, Nothing, ScopedCache[String, Throwable, Connection]] =
  ScopedCache.make(
    capacity = 10,
    timeToLive = 1.minute,
    lookup = openConnection
  )
```

To decide the time to live from the result of the lookup, use `ScopedCache.makeWith`. Its time to live function receives the `Exit` of the lookup, so a failed lookup can expire sooner than a successful one:

```scala
import zio._
import zio.cache.{ScopedCache, ScopedLookup}

final case class Connection(url: String)

val openConnection: ScopedLookup[String, Scope, Nothing, Connection] =
  ScopedLookup { (url: String) =>
    ZIO.acquireRelease(ZIO.succeed(Connection(url)))(connection =>
      ZIO.debug(s"Closing connection to ${connection.url}")
    )
  }

val connections: URIO[Scope, ScopedCache[String, Throwable, Connection]] =
  ScopedCache.makeWith(capacity = 10, scopedLookup = openConnection)(exit =>
    if (exit.isSuccess) 1.minute else 5.seconds
  )
```

## Using Scoped Values

The `ScopedCache#get` operator returns the cached value as a `ZIO[Scope, Error, Value]`. The `Scope` in its type tells the caller how long it uses the value. The cache counts each caller that holds the value, so the value stays alive until every caller's scope closes. A caller must not use the value after its scope closes.

To use a value for one operation, run `ScopedCache#get` inside `ZIO.scoped`, which opens a scope and closes it when the operation finishes:

```scala
import zio._
import zio.cache.{ScopedCache, ScopedLookup}

final case class Connection(url: String)

def query(cache: ScopedCache[String, Throwable, Connection], url: String): Task[Unit] =
  ZIO.scoped {
    cache.get(url).flatMap { connection =>
      ZIO.debug(s"Running a query on ${connection.url}")
    }
  }
```

## Lifetime of Scoped Values

A resource is released only after two things are true. First, the value has left the cache, because it was evicted, invalidated, expired, or the cache itself was closed. Second, every caller that obtained the value through `get` has closed its scope.

Expired values are removed in two ways. A `ScopedCache#get` call on an expired key removes the old value before it computes a new one. A background fiber also checks the cache about every second and removes all expired values. Both paths release the resources of values that no caller uses.

If the lookup fails, the failure is cached, as with a plain [Cache](./cache.md). The failed lookup releases any resource it acquired before it failed.

## Operators

In addition to `ScopedCache#get`, `ScopedCache` provides these operators:

- `ScopedCache#refresh` computes a new value with the lookup function and replaces the cached one. Concurrent calls to `ScopedCache#get` keep using the old value until the new one is ready, and the old resource is released once no caller uses it.
- `invalidate` removes the value for a key from the cache. The resource is released after its last caller closes its scope.
- `invalidateAll` removes all values from the cache.
- `ScopedCache#contains` returns whether the cache holds a value for a key. Unlike `Cache#contains`, it does not check expiry: an expired value still counts until the background fiber removes it (see [Lifetime of Scoped Values](#lifetime-of-scoped-values)).
- `size` returns the approximate number of values in the cache. It counts expired values that the background fiber has not removed yet.
- `cacheStats` returns a snapshot of the cache statistics. See [Cache Statistics](./cache-statistics.md).
- `entryStats` returns the statistics of one entry. See [Entry Statistics](./entry-statistics.md).

The following example loads a connection, uses it, and then invalidates it, which releases the connection because no caller uses it anymore:

```scala
import zio._
import zio.cache.{ScopedCache, ScopedLookup}

final case class Connection(url: String)

object ScopedCacheExample extends ZIOAppDefault {

  val openConnection: ScopedLookup[String, Scope, Nothing, Connection] =
    ScopedLookup { (url: String) =>
      ZIO.acquireRelease(ZIO.debug(s"Opening connection to $url").as(Connection(url)))(connection =>
        ZIO.debug(s"Closing connection to ${connection.url}")
      )
    }

  def run =
    ZIO.scoped {
      for {
        cache <- ScopedCache.make(capacity = 10, timeToLive = 1.minute, lookup = openConnection)
        _ <- ZIO.scoped {
               cache.get("db-1").flatMap(connection => ZIO.debug(s"Using ${connection.url}"))
             }
        _ <- cache.invalidate("db-1")
      } yield ()
    }
}
```
