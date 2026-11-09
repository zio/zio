# Running Queries and Caching

> A `ZQuery` is only a description of work. To get a result, we convert it to a `ZIO` effect with one of three methods, and every one of them runs the query with a `Cache` that deduplicates requests. This page explains what each method does, how the cache interacts with batching, and when to share a cache between runs.

## Overview

The three methods differ in who owns the cache:

| Method                   | Cache                                      | Returns                 |
| ------------------------ | ------------------------------------------ | ----------------------- |
| `ZQuery#run`             | A new, empty cache created for this run    | `ZIO[R, E, A]`          |
| `ZQuery#runCache(cache)` | The cache you pass in                      | `ZIO[R, E, A]`          |
| `ZQuery#runLog`          | A new, empty cache that is returned to you | `ZIO[R, E, (Cache, A)]` |

`ZQuery#run` is the right choice in most cases. `ZQuery#runCache` is for sharing or replaying results across runs, and `ZQuery#runLog` is for inspecting what a run executed.

## Setting Up an Example

To observe how often a data source is called, we need one that reports its calls. The following code defines a request type, a data source that prints the batches it receives, and a helper that runs an effect to completion:

```scala
import zio._
import zio.query._

final case class GetUserName(id: Int) extends Request[Nothing, String]

val userDataSource: DataSource[Any, GetUserName] =
  DataSource.make[Any, GetUserName]("UserDataSource") { requests =>
    ZIO.succeed {
      println(s"UserDataSource received: ${requests.map(_.map(_.id).mkString("[", ", ", "]")).mkString(" ")}")
      CompletedRequestMap.fromIterableWith(requests.flatten)(r => r, r => Exit.succeed(s"user-${r.id}"))
    }
  }

def userName(id: Int): ZQuery[Any, Nothing, String] =
  ZQuery.fromRequest(GetUserName(id))(userDataSource)

def exec[A](effect: ZIO[Any, Nothing, A]): A =
  Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(effect).getOrThrowFiberFailure())
```

In the output, each pair of square brackets is one batch of requests. [How Batching, Pipelining, and Parallelism Work](./how-batching-works.md) explains how queries produce batches.

## Running a Query

`ZQuery#run` returns an effect that executes the query and produces its result. Each call to `ZQuery#run` creates a new empty cache, so the effect starts from scratch every time it is run:

```scala
val query = ZQuery.foreachPar(List(1, 2, 3))(userName)
```

Running it twice calls the data source twice:

```scala
exec(query.run)
// UserDataSource received: [1, 2, 3]
// res0: List[String] = List("user-1", "user-2", "user-3")

exec(query.run)
// UserDataSource received: [1, 2, 3]
// res1: List[String] = List("user-1", "user-2", "user-3")
```

The data source is called once for each run. The cache lives only as long as the run, so results from the first run are not available to the second.

## How the Cache Works

While a query runs, every request built with `ZQuery.fromRequest` (or `ZQuery.fromRequests` and `ZQuery.fromRequestsWith`) is looked up in the cache before it reaches the data source. The cache is keyed by the request itself, using `equals` and `hashCode`, so requests should be case classes. For each new request, the cache stores a `Promise` that is completed with the result when the data source answers. A later equal request finds that promise and waits for it instead of calling the data source again.

This has three consequences. First, equal requests in the same batch are sent to the data source once:

```scala
exec(ZQuery.foreachPar(List(1, 1, 2, 2))(userName).run)
// UserDataSource received: [1, 2]
// res2: List[String] = List("user-1", "user-1", "user-2", "user-2")
```

Second, equal requests in different steps of a query are answered from the cache, even when the second one depends on the first through `ZQuery#flatMap`:

```scala
exec(userName(1).flatMap(_ => userName(1)).run)
// UserDataSource received: [1]
// res3: String = "user-1"
```

Third, if a data source returns results for requests it did not receive, the cache stores them too. A later request for one of those results is answered without another call. Only requests built with `ZQuery.fromRequest` or one of its variants are cached. An effect lifted with `ZQuery.fromZIO` is never cached, whatever it does internally.

## Disabling and Enabling Caching

Caching is enabled by default. To send every request to the data source, even if an equal request was already sent, call `ZQuery#uncached`:

```scala
exec(ZQuery.foreachPar(List(1, 1, 2))(userName).uncached.run)
// UserDataSource received: [1, 1, 2]
// res4: List[String] = List("user-1", "user-1", "user-2")
```

Both copies of the request for user 1 reach the data source. Disabling caching applies only to the query it is called on, so we can disable it for one part of a larger query and leave the rest cached.

To re-enable caching inside a part of a query where it was disabled, call `ZQuery#cached`. It has an effect only inside `ZQuery#uncached`, because caching is already enabled elsewhere. To skip the cache for a single request without wrapping a query, construct it with `ZQuery.fromRequestUncached` instead of `ZQuery.fromRequest`.

Use these when a request must observe fresh data, for example a read that follows a write in the same query.

## Inspecting a Run With runLog

`ZQuery#runLog` runs the query with a new cache and returns that cache together with the result. Since the cache holds every request the run submitted and its outcome, it works as a log of the run:

```scala
val (log, result) = exec(userName(1).zipPar(userName(2)).runLog)
```

To read an entry from the log, call `Cache#get` with the request, giving the request's error and result types explicitly because the compiler cannot infer them from a case class. It fails with `()` if the request is not in the cache, and otherwise returns the `Promise` that holds the result:

```scala
exec(log.get[Nothing, String](GetUserName(1)).flatMap(_.await).orDieWith(_ => new NoSuchElementException))
// res5: String = "user-1"

exec(log.get[Nothing, String](GetUserName(99)).fold(_ => "not in cache", _ => "in cache"))
// res6: String = "not in cache"
```

Use `ZQuery#runLog` in tests to assert which requests a query submitted, or in diagnostics to see what a slow query fetched. Because the log is a `Cache`, it can also be passed to `ZQuery#runCache`.

## Sharing a Cache With runCache

`ZQuery#runCache` runs a query with a cache you provide. To create one, call `Cache.empty`, or `Cache.empty(expectedNumOfElements)` to presize it when you know roughly how many requests it will hold. Running two queries with the same cache means the second one sees the results of the first:

```scala
val cache = exec(Cache.empty)
```

The first run fetches user 1 and the second run fetches only user 2:

```scala
exec(userName(1).runCache(cache))
// UserDataSource received: [1]
// res7: String = "user-1"

exec(userName(1).zipPar(userName(2)).runCache(cache))
// UserDataSource received: [2]
// res8: (String, String) = ("user-1", "user-2")
```

The second run calls the data source only for user 2. The result for user 1 comes from the cache, which is also why a cache from `ZQuery#runLog` can be used to replay a query without executing any new requests:

```scala
exec(userName(1).zipPar(userName(2)).runCache(log))
// res9: (String, String) = ("user-1", "user-2")
```

Nothing is printed, because the data source is never called.

### When to Share a Cache

A shared cache keeps its entries for as long as the cache itself exists. The default cache has no eviction, no size limit, and no expiry, so sharing is a trade-off:

- Share a cache across the runs that make up one logical operation, for example the several queries that handle one incoming request, so that they do not fetch the same data twice.
- Share a cache to replay a recorded run deterministically in a test, with no data source calls.
- Do not keep one cache for the lifetime of an application. Results become stale, and memory grows with every distinct request.

To drop a stale entry from a shared cache, call `Cache#remove` with the request. The next equal request goes to the data source again:

```scala
exec(cache.remove(GetUserName(1)))

exec(userName(1).runCache(cache))
// UserDataSource received: [1]
// res11: String = "user-1"
```

## Caching and Memoization

The cache stores results of requests to data sources. It does not store the result of a query as a whole, so work done by `ZQuery.fromZIO` effects or by `ZQuery#map` is repeated each time a query value is used. When one query value is used in several places and its non-request work must run once, call `ZQuery#memoize`. It returns an effect that produces a query that computes at most once, including its failures:

```scala
val counter   = exec(Ref.make(0))
val expensive = ZQuery.fromZIO(counter.updateAndGet(_ + 1))
val memoized  = exec(expensive.memoize)
```

Using the memoized query twice yields the same value, while using the plain query twice runs its effect twice:

```scala
exec(memoized.zip(memoized).run)
// res12: (Int, Int) = (1, 1)

exec(counter.set(0))

exec(expensive.zip(expensive).run)
// res14: (Int, Int) = (1, 2)
```

The memoized query runs its effect once and the plain query runs it twice, whether or not caching is enabled. The counter is reset in between so that the two results are comparable.

## Custom Caches

`Cache` is a trait with four methods: `Cache#get`, `Cache#lookup`, `Cache#put`, and `Cache#remove`. `Cache.empty` returns an implementation backed by a concurrent hash map. To add eviction or expiry, implement `Cache` and pass the instance to `ZQuery#runCache`. `Cache#lookup` is the method the query runtime calls for every request. When the request is not in the cache, `Cache#lookup` returns a `Left` with a new `Promise` that the runtime completes with the result, and the cache must keep that promise so that later equal requests find it. When the request is in the cache, it returns a `Right` with the existing `Promise`.
