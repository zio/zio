# How Batching, Pipelining, and Parallelism Work

> ZIO Query reduces the number of calls to a data source by looking at how queries are composed. This page explains the three ideas behind that optimization (batching, pipelining, and deduplication), which combinators trigger each one, and what a data source receives as a result.

## Overview

Every request in a query is submitted to a [data source](./creating-data-sources.md), and a data source receives its requests through `DataSource#runAll`, whose parameter is a `Chunk[Chunk[A]]`. The shape of that value is what the optimizations are about:

```
runAll(requests: Chunk[ Chunk[A], Chunk[A], ... ])
              │          │
              │          └─ a second batch: runs only after the first batch completes
              └─ one batch: requests that can be performed in parallel
```

The outer `Chunk` holds batches that must be performed one after another. Each inner `Chunk` holds requests that can be performed at the same time. ZIO Query builds this value from the structure of the query:

- **Batching** puts requests that can run in parallel into the same inner `Chunk`, so a data source can answer them with one call (for example, one SQL query with `WHERE id IN (...)`).
- **Pipelining** puts requests that must run in sequence into one `DataSource#runAll` call as separate inner `Chunk`s, so a data source can still optimize across them (for example, send both in one network round trip).
- **Deduplication** removes repeated requests, so the same request is sent to the data source only once while caching is enabled.

The following table summarizes which combinators produce which behavior:

| Composition                                         | Requests are                | Effects other than requests run |
| --------------------------------------------------- | --------------------------- | ------------------------------- |
| `ZQuery#zipPar` (`<&>`, `&>`, `<&`)                 | Batched                     | In parallel                     |
| `ZQuery.foreachPar`, `ZQuery.collectAllPar`         | Batched                     | In parallel                     |
| `ZQuery#zipBatched` (`<~>`, `~>`, `<~`)             | Batched                     | In sequence                     |
| `ZQuery.foreachBatched`, `ZQuery.collectAllBatched` | Batched                     | In sequence                     |
| `ZQuery#zip` (`<*>`, `*>`, `<*`)                    | Pipelined                   | In sequence                     |
| `ZQuery.foreach`, `ZQuery.collectAll`               | Pipelined                   | In sequence                     |
| `ZQuery#flatMap`                                    | Neither (one call per step) | In sequence                     |

## Setting Up an Example

To see what a data source receives, we need a data source that records its calls. The following code defines two request types, one data source for each, and a helper that runs a query and prints its result together with the calls the data sources received:

```scala
import zio._
import zio.query._

final case class GetUserName(id: Int)   extends Request[Nothing, String]
final case class GetOrderCount(id: Int) extends Request[Nothing, Int]

val calls: Ref[Chunk[String]] =
  Unsafe.unsafe(implicit unsafe => Ref.unsafe.make(Chunk.empty[String]))

def batches[A](requests: Chunk[Chunk[A]])(id: A => Int): String =
  requests.map(_.map(id).mkString("[", ", ", "]")).mkString(" ")

val userDataSource: DataSource[Any, GetUserName] =
  DataSource.make[Any, GetUserName]("UserDataSource") { requests =>
    calls.update(_ :+ s"UserDataSource: ${batches(requests)(_.id)}") *>
      ZIO.succeed(
        CompletedRequestMap.fromIterableWith(requests.flatten)(r => r, r => Exit.succeed(s"user-${r.id}"))
      )
  }

val orderDataSource: DataSource[Any, GetOrderCount] =
  DataSource.make[Any, GetOrderCount]("OrderDataSource") { requests =>
    calls.update(_ :+ s"OrderDataSource: ${batches(requests)(_.id)}") *>
      ZIO.succeed(
        CompletedRequestMap.fromIterableWith(requests.flatten)(r => r, r => Exit.succeed(r.id * 10))
      )
  }

def userName(id: Int): ZQuery[Any, Nothing, String] =
  ZQuery.fromRequest(GetUserName(id))(userDataSource)

def orderCount(id: Int): ZQuery[Any, Nothing, Int] =
  ZQuery.fromRequest(GetOrderCount(id))(orderDataSource)

def show[A](query: ZQuery[Any, Nothing, A]): Unit = {
  val (result, received) = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(query.run <*> calls.getAndSet(Chunk.empty)).getOrThrowFiberFailure()
  }
  println(s"result: $result")
  received.sorted.foreach(println)
}
```

In the output below, each pair of square brackets is one inner `Chunk`, that is, one batch of requests that can be performed in parallel. Several pairs in one line are sequential batches of the same `DataSource#runAll` call.

## Batching

Batching applies when queries are combined in parallel. To fetch three users with `ZQuery.foreachPar`:

```scala
show(ZQuery.foreachPar(List(1, 2, 3))(userName))
// result: List(user-1, user-2, user-3)
// UserDataSource: [1, 2, 3]
```

The data source receives one call, with all three requests in one batch. This is how ZIO Query avoids the N + 1 problem: the number of calls to the data source depends on the number of sequential steps in the query, not on the number of requests.

Requests to different data sources are batched per data source, and the data sources run in parallel with each other. To combine a user lookup and an order lookup with `ZQuery#zipPar`:

```scala
show(userName(1) <&> orderCount(1))
// result: (user-1,10)
// OrderDataSource: [1]
// UserDataSource: [1]
```

Each data source receives one call. Batching also works across larger queries: when parallel branches each contain their own `ZQuery#zip`, the requests of all branches join the same batch. To collect two branches that each fetch two users:

```scala
show(ZQuery.collectAllPar(List(userName(1).zip(userName(2)), userName(3).zip(userName(4)))))
// result: List((user-1,user-2), (user-3,user-4))
// UserDataSource: [1, 3] [4, 2]
```

The data source receives all four requests in one batch, even though each pair was written as a separate query.

When a batch is too large for the data source, `DataSource#batchN` (or the `QueryAspect.maxBatchSize` aspect) limits how many requests run in parallel. See [Aspects](./aspects.md#limiting-the-batch-size).

## Pipelining

Pipelining applies when queries are combined sequentially with `ZQuery#zip` or a combinator derived from it. To fetch three users one after another with `ZQuery.foreach`:

```scala
show(ZQuery.foreach(List(1, 2, 3))(userName))
// result: List(user-1, user-2, user-3)
// UserDataSource: [1] [2] [3]
```

The data source still receives one call, but with three batches of one request each. The batches must run in order, but the data source sees all of them at the same time, so it can send them together, for example in one round trip. A data source that extends `DataSource.Batched` does not use this information: it receives the same call, but it runs `DataSource.Batched#run` once for each batch, in order.

## Sequencing With `ZQuery#flatMap`

When the second query depends on the result of the first, the requests cannot be known in advance. `ZQuery#flatMap` therefore starts a new step, and the data source receives one call for each step:

```scala
show(userName(1).flatMap(_ => userName(2)))
// result: user-2
// UserDataSource: [1]
// UserDataSource: [2]
```

Compare this with `userName(1) *> userName(2)`, which has the same requests but does not use the first result to build the second query:

```scala
show(userName(1) *> userName(2))
// result: user-2
// UserDataSource: [1] [2]
```

A `ZQuery#flatMap` between two requests prevents them from being pipelined, while a `ZQuery#flatMap` on the last request does not.

## Batching Sequential Queries

Sometimes a query must run its effects in order, but its requests do not depend on each other. `ZQuery#zipBatched` (and its aliases `<~>`, `~>`, `<~`) runs effects sequentially and still batches the requests:

```scala
show(userName(1) <~> userName(2))
// result: (user-1,user-2)
// UserDataSource: [1, 2]
```

Both requests are in the same batch, so the data source can answer them with one call. Use `ZQuery.foreachBatched` and `ZQuery.collectAllBatched` for the collection versions.

## Deduplication

While caching is enabled, which is the default, a request that equals an earlier request in the same run is not sent again. To fetch the same user twice:

```scala
show(ZQuery.foreachPar(List(1, 1, 2))(userName))
// result: List(user-1, user-1, user-2)
// UserDataSource: [1, 2]
```

The data source receives each distinct request once. Equality is determined by `equals` on the request, so define requests as case classes. To send every request to the data source, disable caching with `ZQuery#uncached`:

```scala
show(ZQuery.foreachPar(List(1, 1, 2))(userName).uncached)
// result: List(user-1, user-1, user-2)
// UserDataSource: [1, 1, 2]
```

## Limiting Parallelism

Combinators with `Par` in their name run effects in parallel. To set the maximum number of fibers a query can use, call `ZQuery#withParallelism`, or call `ZQuery#withParallelismUnbounded` to remove the limit.

## Writing Queries That Optimize Well

The optimizations apply only to requests built with `ZQuery.fromRequest` or one of its variants. An effect lifted with `ZQuery.fromZIO` is opaque to ZIO Query, even if it calls a database, so it is never batched, pipelined, or deduplicated.

To get the most out of the optimizations:

- Combine independent queries with `ZQuery#zipPar`, `ZQuery.foreachPar`, or `ZQuery.collectAllPar` rather than `ZQuery#flatMap`.
- Use `ZQuery#flatMap` only when a later query needs the result of an earlier one.
- Define each data source once, as a `val` or an `object`, because requests are grouped by data source.
- Define requests as case classes so that equal requests are deduplicated.
