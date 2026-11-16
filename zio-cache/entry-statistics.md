# Entry Statistics

> ZIO Cache also tracks statistics for each entry, such as when the entry was loaded into the cache. You can access the statistics for a specified entry using the `entryStats` operator on `Cache`, passing the key of the entry.

The `EntryStats` type is a case class with one field, `loaded`:

```scala
import zio._
import zio.cache.{Cache, EntryStats}

def loadedAt(cache: Cache[String, Nothing, Int], key: String): UIO[Option[java.time.Instant]] =
  cache.entryStats(key).map(_.map((stats: EntryStats) => stats.loaded))
```

`loaded` is the instant when the lookup for the entry completed. `entryStats` returns `None` when the cache holds no value for that key, including while the first lookup for the key is still running. An expired value that has not been removed yet still returns its statistics.

More entry statistics will be added over time.
