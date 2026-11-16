# Cache Statistics

> ZIO Cache automatically tracks statistics for the whole cache: the number of cache hits and misses and the current size of the cache. You can use them to assess how well the cache works. You can access these statistics by using the `cacheStats` operator on `Cache`.

The `CacheStats` type is a case class with three fields:

```scala
import zio._
import zio.cache.{Cache, CacheStats}

def report(cache: Cache[String, Nothing, Int]): UIO[Unit] =
  cache.cacheStats.flatMap { stats: CacheStats =>
    ZIO.debug(s"hits=${stats.hits} misses=${stats.misses} size=${stats.size}")
  }
```

- `hits` is the number of lookups answered without running the lookup function. A lookup that waits for a value another fiber is still computing counts as a hit. For `Cache`, a lookup that finds an expired value counts as a miss.
- `misses` is the number of lookups that started a new computation.
- `size` is the number of values in the cache. Under concurrent access it is approximate.

The cache statistics represent a snapshot as of a point in time. Note that the cache statistics are designed for aggregate analysis and may not be fully consistent with each other when read during concurrent updates.

More cache statistics will be added over time. To track the statistics of one entry, see [Entry Statistics](./entry-statistics.md).
