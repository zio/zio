# CompletedRequestMap

> A `CompletedRequestMap` is what a [data source](./creating-data-sources.md) returns from `runAll`: a mapping from each request it executed to the result of that request, an `Exit[E, A]`. This page explains how to build one, how to look results up in it, which methods are deprecated, and how `ZQuery.fromRequests` submits several requests to a data source at once.

## Setting Up an Example

The examples on this page use the following request types:

```scala
import zio._
import zio.query._

final case class GetUserName(id: Int) extends Request[String, String]
final case class GetOrderCount(id: Int) extends Request[Nothing, Int]
```

## How a Query Reads the Map

A `CompletedRequestMap` is keyed by requests, and the type of each entry is tied to its request: a `Request[E, A]` is always mapped to an `Exit[E, A]`. This lets one data source answer requests of different result types.

After `runAll` returns, the query looks up the result of each request it submitted, using request equality. If the map has no entry for a request, the query dies with a `QueryFailure` that names the data source and the request. Make sure that the map has an entry for every request the data source received, and that each key is equal to the request the query submitted. Because requests are compared with `equals`, define them as case classes (or case objects).

You can read a map with:

| Method                      | Returns                                                           |
| --------------------------- | ----------------------------------------------------------------- |
| `lookup(request)`           | `Option[Exit[E, A]]`, the result of the request if there is one   |
| `contains(request)`         | `Boolean`, whether the map has a result for the request           |
| `requests`                  | `Set[Request[_, _]]`, all requests in the map                     |
| `isEmpty`                   | `Boolean`, whether the map has no entries                         |

## Creating a Map

All constructors are in the `CompletedRequestMap` companion object.

### From a Single Result or Several Results

`single` creates a map with one entry, `empty` creates a map with none, and `apply` takes entries as `(request, exit)` pairs:

```scala
val one: CompletedRequestMap =
  CompletedRequestMap.single(GetUserName(1), Exit.succeed("Ann"))

val several: CompletedRequestMap =
  CompletedRequestMap(
    GetUserName(1) -> Exit.succeed("Ann"),
    GetUserName(2) -> Exit.fail("no such user")
  )
```

```scala
several.lookup(GetUserName(1))
// res0: Option[Exit[String, String]] = Some(value = Success(value = "Ann"))
several.lookup(GetUserName(2))
// res1: Option[Exit[String, String]] = Some(
//   value = Failure(
//     cause = Fail(
//       value = "no such user",
//       trace = StackTrace(fiberId = None, stackTrace = IndexedSeq())
//     )
//   )
// )
several.lookup(GetUserName(3))
// res2: Option[Exit[String, String]] = None
several.contains(GetUserName(3))
// res3: Boolean = false
```

All entries given to `apply` must have the same error and result types. Use `combine` (see below) to put results of requests with different types in one map.

### From a Collection

Data sources usually get their results from a database or a remote service as a collection. The following constructors build a map from a collection in one pass:

| Constructor                                           | Use when                                                                                      |
| ----------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `fromIterable(iterable)`                              | you already have `(request, exit)` pairs                                                      |
| `fromIterableWith(iterable)(toRequest, toExit)`       | you have your own values and functions that convert each of them to a request and to an `Exit` |
| `fromIterableOption(iterable)`                        | each result is an `Exit[E, Option[A]]` and `None` means that the entry does not exist         |

`fromIterableWith` avoids creating an intermediate collection of pairs. For example, a database query that returns rows of `(id, name)` can be turned into a map directly:

```scala
val rows: List[(Int, String)] = List(1 -> "Ann", 2 -> "Bob")

val fromRows: CompletedRequestMap =
  CompletedRequestMap.fromIterableWith(rows)(
    row => GetUserName(row._1),
    row => Exit.succeed(row._2)
  )
```

`fromIterableOption` is for lookups that may find nothing. A pair whose result is `Exit.succeed(Some(a))` is stored as a success with `a`, a failure is stored as a failure, and a pair with `Exit.succeed(None)` is left out of the map. A request that is left out is not completed, so the query dies with a `QueryFailure` for it, unless another map that you combine with this one has a result for it:

```scala
val withMissing: CompletedRequestMap =
  CompletedRequestMap.fromIterableOption(
    List(
      GetOrderCount(1) -> Exit.succeed(Some(10)),
      GetOrderCount(2) -> Exit.succeed(None)
    )
  )
```

```scala
withMissing.lookup(GetOrderCount(1))
// res4: Option[Exit[Nothing, Int]] = Some(value = Success(value = 10))
withMissing.contains(GetOrderCount(2))
// res5: Boolean = false
```

### Failing Several Requests at Once

When a whole call fails, use `fail`, `failCause` or `die` to complete all requests of a batch with the same failure. Each takes a `Chunk` of requests and the error, the `Cause` or the `Throwable`:

```scala
val requests: Chunk[GetUserName] = Chunk(GetUserName(1), GetUserName(2))

val failed: CompletedRequestMap =
  CompletedRequestMap.fail(requests, "database is down")

val failedWithCause: CompletedRequestMap =
  CompletedRequestMap.failCause(requests, Cause.fail("database is down"))

val died: CompletedRequestMap =
  CompletedRequestMap.die(requests, new RuntimeException("bug"))
```

See [Error Handling](./error-handling.md) for what each kind of failure means for the query.

### Combining Maps

`combine` merges an `Iterable` of maps into one. If several maps have an entry for the same request, the entry from the last map wins:

```scala
val combined: CompletedRequestMap =
  CompletedRequestMap.combine(
    List(
      CompletedRequestMap.single(GetUserName(1), Exit.succeed("Ann")),
      CompletedRequestMap.single(GetOrderCount(1), Exit.succeed(10)),
      CompletedRequestMap.single(GetUserName(1), Exit.succeed("Anna"))
    )
  )
```

```scala
combined.lookup(GetUserName(1))
// res6: Option[Exit[String, String]] = Some(value = Success(value = "Anna"))
combined.lookup(GetOrderCount(1))
// res7: Option[Exit[Nothing, Int]] = Some(value = Success(value = 10))
```

### From Parallel Collections of Requests and Results

`CompletedRequestMap.unsafe` has constructors that take a `Chunk` of requests and a `Chunk` of results in the same order, which is the shape many batch APIs return. They skip the creation of intermediate pairs, which can improve performance:

| Method                                                | Builds a map from                                                          |
| ----------------------------------------------------- | -------------------------------------------------------------------------- |
| `unsafe.fromExits(requests, responses)`               | a `Chunk` of requests and a `Chunk` of `Exit`s                             |
| `unsafe.fromSuccesses(requests, responses)`           | a `Chunk` of requests and a `Chunk` of successful values                   |
| `unsafe.fromWith(requests, responses)(toRequest, toExit)` | two `Chunk`s of any element types and functions that convert them      |

```scala
val fromChunks: CompletedRequestMap =
  CompletedRequestMap.unsafe.fromSuccesses(
    Chunk(GetUserName(1), GetUserName(2)),
    Chunk("Ann", "Bob")
  )
```

These methods are called unsafe because they do not check that the two collections have the same size. They pair the elements by position and stop at the end of the shorter one. It is up to you to make sure that the nth result belongs to the nth request.

## Deprecated Methods

`++`, `insert` and `insertOption` are deprecated since version 0.7.0. A `CompletedRequestMap` created by the constructors above is built by filling one mutable map, which is fast. These three methods can't change that map without exposing the mutation, so each call copies all entries into a new immutable map. Code that builds a map by folding with `insert` or `++` over many requests does not benefit from the faster constructors, and each step costs time that grows with the size of the map.

Replace them as follows:

| Deprecated                                    | Replacement                                                                                              |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `map.insert(request, exit)`                   | `CompletedRequestMap.single(request, exit)`, or `fromIterable` for many entries, then `combine`          |
| `map.insertOption(request, exit)`             | `CompletedRequestMap.fromIterableOption`                                                                 |
| `map1 ++ map2`                                | `CompletedRequestMap.combine(List(map1, map2))`                                                          |
| `list.foldLeft(empty)(_.insert(k, v))`        | `CompletedRequestMap.fromIterable` or `fromIterableWith`                                                 |

The following two maps are equal in content. The second one is built in a single pass:

```scala
@scala.annotation.nowarn("cat=deprecation")
val slow: CompletedRequestMap =
  rows.foldLeft(CompletedRequestMap.empty) { case (map, (id, name)) =>
    map.insert(GetUserName(id), Exit.succeed(name))
  }

val fast: CompletedRequestMap =
  CompletedRequestMap.fromIterableWith(rows)(row => GetUserName(row._1), row => Exit.succeed(row._2))
```

## Submitting Several Requests at Once

`ZQuery.fromRequest` submits one request to a data source. When you have many requests for the same data source, `ZQuery.fromRequests` takes a `Chunk` or a `List` of requests and returns a query for all their results, in the same collection type and in the same order as the requests:

```scala
def fromRequests[R, E, A, B](requests: Chunk[A])(dataSource: DataSource[R, A]): ZQuery[R, E, Chunk[B]]
def fromRequests[R, E, A, B](requests: List[A])(dataSource: DataSource[R, A]): ZQuery[R, E, List[B]]
```

`fromRequestsWith` also takes a function that converts each element of the collection into a request, so you don't have to build the requests first:

```scala
def fromRequestsWith[R, E, In, A, B](as: Chunk[In], f: In => A)(dataSource: DataSource[R, A]): ZQuery[R, E, Chunk[B]]
def fromRequestsWith[R, E, In, A, B](as: List[In], f: In => A)(dataSource: DataSource[R, A]): ZQuery[R, E, List[B]]
```

The requests are submitted as one batch, as with `ZQuery#zipBatched`, so the data source receives them together in one call. [How Batching, Pipelining, and Parallelism Work](./how-batching-works.md) explains what that means for the data source. To try them, the following data source answers `GetUserName` requests from a map and records the batches it receives:

```scala
val calls: Ref[Chunk[String]] =
  Unsafe.unsafe(implicit unsafe => Ref.unsafe.make(Chunk.empty[String]))

val userDataSource: DataSource[Any, GetUserName] =
  DataSource.make[Any, GetUserName]("UserDataSource") { requests =>
    calls.update(_ :+ requests.map(_.map(_.id).mkString("[", ", ", "]")).mkString(" ")) *>
      ZIO.succeed(
        CompletedRequestMap.fromIterableWith(requests.flatten)(
          request => request,
          request => if (request.id > 0) Exit.succeed(s"user-${request.id}") else Exit.fail("invalid id")
        )
      )
  }

def show[E, A](query: ZQuery[Any, E, A]): Unit = {
  val (result, received) = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(query.run.exit <*> calls.getAndSet(Chunk.empty)).getOrThrowFiberFailure()
  }
  println(s"result: $result")
  println(s"calls: ${received.mkString(", ")}")
}
```

```scala
show(ZQuery.fromRequests(List(GetUserName(1), GetUserName(2), GetUserName(3)))(userDataSource))
// result: Success(List(user-1, user-2, user-3))
// calls: [1, 2, 3]
```

```scala
show(ZQuery.fromRequestsWith(Chunk(3, 2, 1), GetUserName(_))(userDataSource))
// result: Success(Chunk(user-3,user-2,user-1))
// calls: [3, 2, 1]
```

If one of the requests fails, so does the query:

```scala
show(ZQuery.fromRequests(List(GetUserName(1), GetUserName(0)))(userDataSource))
// result: Failure(Fail(invalid id,StackTrace.none))
// calls: [1, 0]
```

### Skipping the Cache

Like `ZQuery.fromRequest`, `fromRequests` and `fromRequestsWith` use the query cache. `ZQuery.fromRequestUncached` is a variant of `ZQuery.fromRequest` that does not. A repeated request is sent to the data source again, instead of reusing the earlier result. In the output below, the first query sends the request to the data source twice and the second sends it once:

```scala
show(ZQuery.fromRequestUncached(GetUserName(1))(userDataSource) *> ZQuery.fromRequestUncached(GetUserName(1))(userDataSource))
// result: Success(user-1)
// calls: [1] [1]
```

```scala
show(ZQuery.fromRequest(GetUserName(1))(userDataSource) *> ZQuery.fromRequest(GetUserName(1))(userDataSource))
// result: Success(user-1)
// calls: [1]
```
