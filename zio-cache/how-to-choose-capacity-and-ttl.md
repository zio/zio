# How to Choose Capacity and Time to Live

> Pick a ZIO Cache capacity and time to live from your key count, memory budget, and how stale a value may be.

Capacity and time to live are the two settings that decide how much a cache keeps and how long it keeps it. This guide shows how to choose both for a service that caches user profiles.

## Choose the Capacity

The capacity is the maximum number of values the cache holds. When the cache is full, it evicts the least recently accessed value to make room for a new one. A good capacity covers the keys that are requested again within a short period. Each value costs memory, so the capacity also caps how much memory the cache can use.

Start from the number of distinct keys you expect in the busiest period, and multiply it by the approximate size of one value to estimate memory use. If the estimate fits your memory budget, use that number as the capacity:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, String] =
  ZIO.succeed(s"profile of $userId")

val profiles: UIO[Cache[String, String, String]] =
  Cache.make(
    capacity = 10000,
    timeToLive = 5.minutes,
    lookup = Lookup(loadProfile)
  )
```

The size of the cache can briefly exceed the capacity between operations, so treat the capacity as a target rather than an exact limit.

## Choose the Time to Live

The time to live is how long a value may be returned after it was loaded. Choose the longest time that still keeps the data acceptable for your users. A profile that changes rarely can live for minutes. A price that changes every second needs a short time to live, or no cache at all.

Use `Duration.Infinity` only when the value never changes while the process runs. Values then stay until they are evicted by capacity or invalidated by hand.

## Check the Results with Statistics

After you deploy, read the statistics to see whether the settings fit your traffic. A low hit rate means values are evicted or expire before they are reused. A high miss count with a small size suggests the time to live is too short. Use `Cache#cacheStats` for the totals, as described in [How to Monitor the Hit Rate](./how-to-monitor-hit-rate.md).

:::tip
Change one setting at a time and measure again. Changing the capacity and the time to live together makes it hard to tell which change helped.
:::

For the meaning of each parameter, see the [Cache](./cache.md) reference page.
