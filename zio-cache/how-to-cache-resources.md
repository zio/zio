# How to Cache Resources That Must Be Closed

> Keep database connections or file handles in a ZIO ScopedCache, release them on eviction, and replace them after a failed use.

A plain `Cache` is fine for values that need no cleanup. When each value is a resource, such as a database connection, it must be closed when the cache no longer needs it. `ScopedCache` closes each resource after it is evicted, invalidated, or expired, and after the last caller finishes using it.

This guide keeps one connection per database URL. A failed query removes the connection from the cache, so the next call opens a new one. The lookup acquires the connection and registers its release with `ZIO.acquireRelease`, and `runQuery` uses the cache inside a scope:

```scala
import zio._
import zio.cache.{ScopedCache, ScopedLookup}

final case class Connection(url: String) {
  def query(sql: String): IO[Throwable, String] = ZIO.succeed(s"result of $sql on $url")
}

val openConnection: ScopedLookup[String, Scope, Nothing, Connection] =
  ScopedLookup { (url: String) =>
    ZIO.acquireRelease(ZIO.succeed(Connection(url)))(connection =>
      ZIO.debug(s"closing ${connection.url}")
    )
  }

def runQuery(cache: ScopedCache[String, Nothing, Connection], url: String, sql: String): IO[Throwable, String] =
  ZIO.scoped {
    cache.get(url).flatMap(connection => connection.query(sql))
  }.tapError(_ => cache.invalidate(url))
```

`ZIO.acquireRelease` pairs the opening of the connection with its closing. The cache calls the release action only when no caller still uses the connection.

The `ZIO.scoped` block holds the connection only while the query runs. If the query fails, `tapError` invalidates the URL. The connection is then closed once its last caller has finished, and the next `runQuery` call opens a new connection.

The cache itself is built with `ScopedCache.make`, which needs a `Scope`, so the cache lives as long as the enclosing scope. Use `ZIO.scoped` at the top of the program to provide that scope.

:::caution
Do not return the connection from `ZIO.scoped`. The connection is released when the scope closes, so using it afterward uses a closed resource.
:::

For how the cache counts the callers of a resource and when it releases one, see the [Scoped Cache](./scoped-cache.md) reference page.
