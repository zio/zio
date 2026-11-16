# How to Set a Time to Live per Entry

> Use Cache.makeWith to give each ZIO Cache entry its own time to live based on whether its lookup succeeded or failed.

`Cache.make` gives every value the same time to live. When successful and failed lookups need different lifetimes, use `Cache.makeWith` instead. Its time to live function receives the `Exit` of each lookup and returns the time to live for that entry.

Suppose a profile lookup succeeds rarely enough to keep for ten minutes, but a failure should be retried after five seconds. We pass a function to `Cache.makeWith` that checks the `Exit`:

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

The function runs once for each lookup, when the lookup completes. The expiry time of the entry is that moment plus the returned duration. A failed entry expires after five seconds, so the next `Cache#get` for that key runs the lookup again.

:::note
The time to live is fixed when the lookup completes. The function cannot change the lifetime of an entry after it is stored.
:::

To give each entry a time to live based on its key or value rather than its `Exit`, compute the duration from the key in your own code and store values with a separate cache per lifetime.
