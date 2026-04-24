# Cache Statistics

> ZIO Cache automatically tracks various statistics associated with the cache, such as the number of cache hits and misses and the current size of the cache, to allow you to assess the effectiveness of the cache. You can access these statistics by using the `cacheStats` operator on `Cache`.

ZIO Cache automatically tracks various statistics associated with the cache, such as the number of cache hits and misses and the current size of the cache, to allow you to assess the effectiveness of the cache. You can access these statistics by using the `cacheStats` operator on `Cache`.

```scala

trait CacheStats {
  def hits: Long
  def misses: Long
  def size: Int
}

trait Cache[-Key, +Error, +Value] {
  def cacheStats: UIO[CacheStats]
}
```

The cache statistics represent a snapshot as of a point in time. Note that the cache statistics are designed for aggregate analysis and may not be fully consistent.

More cache statistics will be added over time.
