# Cache

> A cache is defined in terms of a lookup function, a capacity, and a time to live.

A cache is defined in terms of a lookup function, a capacity, and a time to live.

```scala

trait Lookup[-Key, -Environment, +Error, +Value]

trait Cache[-Key, +Error, +Value] {
  def get(k: Key): IO[Error, Value]
}

object Cache {

  def make[Key, Environment, Error, Value](
    capacity: Int,
    timeToLive: Duration,
    lookup: Lookup[Key, Environment, Error, Value]
  ): ZIO[Environment, Nothing, Cache[Key, Error, Value]] =
    ???
}
```

The most fundamental operator on a cache is `get`, which either returns the value in the cache if it exists or else computes a new value with the lookup function, puts it in the cache, and returns it.

## Concurrent Access

The cache is guaranteed to be safe for concurrent access. In addition, getting a value from the cache is efficient under concurrency.

If two concurrent processes attempt to get the same value and it does not exist in the cache, the value will be computed once and provided to both concurent processes as soon as it is available. The concurrent processes will semantically block for the value to become available but no underlying operating system threads will block as a result of this operation.

In the event that the computation of the lookup function fails or is interrupted that will automatically be propagated to any concurrent processes waiting for the same value.

Failures will be cached and made available to subsequent calls to `get` to avoid repeatedly computing the same failed value. In the event of interruption the key will be removed from the cache so subsequent calls to `get` will attempt to compute the value again.

## Capacity

A cache is constructed with a specified capacity. When the cache is at capacity the least recently accessed values will be removed first.

Note that the size of the cache may slightly exceed the specified capacity between operations.

## Time To Live (TTL)

A cache also allows specifying a time to live. The cache guarantees that no value will be returned from the cache if its age is greater than or equal to the specified time to live.

The age is calculated based on the interval between when the value was loaded in the cache and when it is accessed.

## Operators

In addition to `get`, `Cache` provides a variety of other operators.

```scala
trait Cache[-Key, +Error, +Value] {
  def cacheStats: UIO[CacheStats]
  def contains(key: Key): UIO[Boolean]
  def entryStats(key: Key): UIO[Option[EntryStats]]
  def invalidate(key: Key): UIO[Unit]
  def invalidateAll: UIO[Unit]
  def refresh(key: Key): IO[Error, Unit]
  def size: UIO[Int]
}
```
The `refresh` operator is similar to `get`. The difference is `refresh` triggers a re-computation of the value without invalidating it. This allows any request to the associated key to be served while the value is being re-computed / retrieved by the lookup function. Note: `refresh` always triggers the lookup function, disregarding the last `Error`. 

The `size` operator returns the current size of the cache. Under concurrent access the size should be regarded as only approximate since by the time we observe a given size the cache may have a different size based on concurrent insertions or removals.

Similarly, the `contains` operator returns whether a value associated with the specified key exists in the cache. Again, under concurrent access `contains` is guaranteed to return whether the cache contains a value associated with the specified key as of a given point in time but that value may be concurrently added or removed immediately after that.

There are also the `cacheStats` and `entryStats` operators which allow obtaining a snapshot of statistics either for the cache itself or for a specified entry. See the sections on cache statistics and entry statistics for further discussion of this functionality.

The `invalidate` and `invalidateAll` operators can be used to evict a value associated with a specified key and evict all values, respectively.
