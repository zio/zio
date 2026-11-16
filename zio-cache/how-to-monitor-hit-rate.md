# How to Monitor the Hit Rate

> Compute a ZIO Cache hit rate from cacheStats, log it on a schedule, and inspect one entry with entryStats.

The hit rate is the share of lookups that the cache answered without running the lookup function. A low hit rate means the cache does little work for your service. This guide computes the hit rate from `Cache#cacheStats`, logs it on a schedule, and then checks a single entry.

The hit rate is the number of hits divided by the number of hits and misses together. The helper below returns zero when no lookups have happened yet. The `monitor` function logs the rate every minute in a background fiber, which is forked into the current scope and stops when that scope closes:

```scala
import zio._
import zio.cache.{Cache, CacheStats}

def hitRate(stats: CacheStats): Double = {
  val lookups = stats.hits + stats.misses
  if (lookups == 0L) 0.0 else stats.hits.toDouble / lookups.toDouble
}

def logHitRate[Key, Error, Value](cache: Cache[Key, Error, Value]): UIO[Unit] =
  cache.cacheStats.flatMap { stats =>
    ZIO.debug(f"hit rate: ${hitRate(stats) * 100}%.1f%% (size ${stats.size})")
  }

def monitor[Key, Error, Value](cache: Cache[Key, Error, Value]): URIO[Scope, Unit] =
  logHitRate(cache).repeat(Schedule.spaced(1.minute)).forkScoped.unit
```

The counters only grow, so the rate above covers the whole life of the cache. To see the rate for a recent period, read the statistics at the start and at the end of the period and subtract the counts.

To check one entry, call `Cache#entryStats` with its key. It returns the time the entry was loaded, or `None` when the cache holds no value for the key. The next example shows that call:

```scala
import zio._
import zio.cache.Cache

def loadedAt[Key, Error, Value](cache: Cache[Key, Error, Value], key: Key): UIO[Option[java.time.Instant]] =
  cache.entryStats(key).map(_.map(_.loaded))
```

A very old `loaded` time for a key that is requested often can mean the time to live is longer than your data allows.

:::tip
Use the rate together with the size. A low rate with a size at the capacity suggests the capacity is too small. A low rate with a small size suggests the time to live is too short, or the keys rarely repeat.
:::

For the fields of each statistics type, see [Cache Statistics](./cache-statistics.md) and [Entry Statistics](./entry-statistics.md).
