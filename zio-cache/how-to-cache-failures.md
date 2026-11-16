# How to Decide Whether to Cache Failures

> Understand how ZIO Cache stores failed lookups and set a short or zero time to live so transient errors are retried.

ZIO Cache stores a failed lookup just like a successful one. The next `Cache#get` for the same key returns the stored error until the entry expires, so the lookup does not run again. This avoids repeated calls to a broken service, but it also means a transient error can be returned for as long as the entry lives.

To decide what fits your service, first pick the lifetime for failures. Use `Cache.makeWith` and return a short time to live when the exit is a failure:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, String] =
  if (userId.isEmpty) ZIO.fail("user id must not be empty")
  else ZIO.succeed(s"profile of $userId")

val profiles: UIO[Cache[String, String, String]] =
  Cache.makeWith(capacity = 1000, lookup = Lookup(loadProfile))(exit =>
    if (exit.isSuccess) 10.minutes else 5.seconds
  )
```

A short lifetime keeps a broken dependency from being hit on every request, and it lets the cache recover after a few seconds. If you do not want failures reused at all, return `Duration.Zero` for failures. The entry then expires as soon as it is stored, and the next `Cache#get` runs the lookup again.

Two details apply to every choice:

- Callers that are already waiting for a lookup in progress receive its failure, even when the failure is not stored for later calls.
- If the lookup is interrupted, the entry is removed and nothing is stored. The next `Cache#get` runs the lookup again.

:::caution
Keeping failures for a long time turns a short outage into a long one. Prefer a short lifetime for failures unless the lookup's errors are permanent, such as a missing record that cannot appear later.
:::

For the per-entry time to live function in more detail, see [How to Set a Time to Live per Entry](./how-to-per-entry-ttl.md).
