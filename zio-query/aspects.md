# Aspects

> An aspect changes how a query or a data source behaves without changing the code that defines it. ZIO Query has two kinds:

- A `DataSourceAspect` transforms a [data source](./creating-data-sources.md) into another data source. Use it to run code around every call to a data source, for example to record metrics or to limit the size of a batch.
- A `QueryAspect` transforms a `ZQuery` into another `ZQuery`. Use it to apply a `DataSourceAspect` to every data source used by a query, or to run code around a whole query.

Aspects are values, so they can be defined once and applied to many queries or data sources. This page shows how to create them, how to apply them with `@@`, and how to use `Described`, which many aspects and data source combinators need.

## Setting Up an Example

The examples on this page use one request type and one data source that records the calls it receives, so that we can see what each aspect changes:

```scala
import zio._
import zio.query._

final case class GetUserName(id: Int) extends Request[Nothing, String]

val log: Ref[Chunk[String]] =
  Unsafe.unsafe(implicit unsafe => Ref.unsafe.make(Chunk.empty[String]))

val userDataSource: DataSource[Any, GetUserName] =
  DataSource.make[Any, GetUserName]("UserDataSource") { requests =>
    log.update(_ :+ s"UserDataSource: ${requests.map(_.map(_.id).mkString("[", ", ", "]")).mkString(" ")}") *>
      ZIO.succeed(
        CompletedRequestMap.fromIterableWith(requests.flatten)(r => r, r => Exit.succeed(s"user-${r.id}"))
      )
  }

def userName(id: Int): ZQuery[Any, Nothing, String] =
  ZQuery.fromRequest(GetUserName(id))(userDataSource)

def show[A](query: ZQuery[Any, Nothing, A]): Unit = {
  val (result, calls) = Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(query.run <*> log.getAndSet(Chunk.empty)).getOrThrowFiberFailure()
  }
  println(s"result: $result")
  calls.foreach(println)
}
```

In the output, each pair of square brackets is one batch of requests that the data source receives. See [How Batching, Pipelining, and Parallelism Work](./how-batching-works.md) for how batches are formed.

## Describing Values with `Described`

Aspects and data source combinators often take a function or an effect together with a description of it. `Described[A]` pairs a value with a `String` description:

```scala
final case class Described[+A](value: A, description: String)
```

The description is used to build the identifier of the data source that the combinator returns. Two data sources are equal when their identifiers are equal, and ZIO Query uses that equality to decide which requests belong to the same data source (and so can be batched together). For this reason, values that are equal should have the same description, and values that are not equal should have different descriptions.

The `?` syntax from `Described` adds a description to any value:

```scala
import zio.query.Described._

val logBefore: Described[UIO[Unit]] =
  log.update(_ :+ "before") ? "log before"

val logAfter: Described[Unit => UIO[Unit]] =
  ((_: Unit) => log.update(_ :+ "after")) ? "log after"
```

`Described` is also required by `DataSource#contramap`, `DataSource#contramapZIO`, `DataSource#eitherWith`, `DataSource#provideEnvironment` and `ZQuery#provideEnvironment`. For example, `provideEnvironment` removes the environment requirement of a data source by supplying a value for it, and the description of that value becomes part of the new data source's identifier:

```scala
val envDataSource: DataSource[Int, GetUserName] =
  DataSource.make[Int, GetUserName]("EnvUserDataSource") { requests =>
    ZIO.serviceWith[Int] { offset =>
      CompletedRequestMap.fromIterableWith(requests.flatten)(r => r, r => Exit.succeed(s"user-${r.id + offset}"))
    }
  }

val providedDataSource: DataSource[Any, GetUserName] =
  envDataSource.provideEnvironment(ZEnvironment(100) ? "offset 100")
```

```scala
providedDataSource.identifier
// res0: String = "EnvUserDataSource.provideSomeEnvironment(_ => offset 100)"
```

:::warning
`DataSource#contramap`, `contramapZIO` and `eitherWith` pass the results of the converted requests through unchanged. The query looks up the result of the original request, so these combinators work only when the converted request is equal to the original one. Otherwise the query dies with a `QueryFailure`.
:::

## Data Source Aspects

### Running Code Around a Data Source

`DataSourceAspect.around` runs an effect before and an effect after every call to the data source's `runAll`. The result of the first effect is passed to the second one. Both are `Described`, and neither can fail. The effects are run with `ZIO.acquireReleaseWith`, so the second one runs even if the call is interrupted:

```scala
def around[R, A](
  before: Described[ZIO[R, Nothing, A]]
)(after: Described[A => ZIO[R, Nothing, Any]]): DataSourceAspect[R]
```

An aspect is applied to a data source with `DataSource#@@`:

```scala
val loggedDataSource: DataSource[Any, GetUserName] =
  userDataSource @@ DataSourceAspect.around(logBefore)(logAfter)
```

The effects run once per call to the data source, not once per request. A query that fetches three users in parallel sends one call:

```scala
show(ZQuery.foreachPar(List(1, 2, 3))(id => ZQuery.fromRequest(GetUserName(id))(loggedDataSource)))
// result: List(user-1, user-2, user-3)
// before
// UserDataSource: [1, 2, 3]
// after
```

The aspect also changes the identifier of the data source. The new identifier is the old one followed by `@@ around(`, the description of the first effect, `)(`, the description of the second effect and `)`:

```scala
loggedDataSource.identifier
// res2: String = "UserDataSource @@ around(log before)(log after)"
```

Because the identifier is different, requests sent to `loggedDataSource` are not batched together with requests sent to `userDataSource`.

### Limiting the Batch Size

`DataSourceAspect.maxBatchSize(n)` splits every batch the data source receives into batches of at most `n` requests. It is the aspect form of `DataSource#batchN`:

```scala
val smallBatches: DataSource[Any, GetUserName] =
  userDataSource @@ DataSourceAspect.maxBatchSize(2)
```

A data source that is limited to two requests receives five parallel requests as three batches, in one call to `runAll`:

```scala
show(ZQuery.foreachPar(List(1, 2, 3, 4, 5))(id => ZQuery.fromRequest(GetUserName(id))(smallBatches)))
// result: List(user-1, user-2, user-3, user-4, user-5)
// UserDataSource: [1, 2] [3, 4] [5]
```

The smaller batches are the sequential batches of the outer `Chunk` described in [Creating Data Sources](./creating-data-sources.md), so a data source that executes them one after another never has more than `n` requests in flight at a time. If `n` is less than 1, the data source dies with an `IllegalArgumentException` when it runs.

### Composing Data Source Aspects

`DataSourceAspect#>>>` (or `andThen`) combines two aspects into one that applies the left aspect first and the right aspect to the result. In the following data source, the batches are split first, and then the log effects wrap the call:

```scala
val limitedAndLogged: DataSource[Any, GetUserName] =
  userDataSource @@ (DataSourceAspect.maxBatchSize(2) >>> DataSourceAspect.around(logBefore)(logAfter))
```

```scala
show(ZQuery.foreachPar(List(1, 2, 3))(id => ZQuery.fromRequest(GetUserName(id))(limitedAndLogged)))
// result: List(user-1, user-2, user-3)
// before
// UserDataSource: [1, 2] [3]
// after
```

`DataSourceAspect[R]` is a trait with a single method, `apply[R1 <: R, A](dataSource: DataSource[R1, A]): DataSource[R1, A]`, so you can also implement your own aspect.

## Query Aspects

A `QueryAspect` is applied to a query with `ZQuery#@@`. The ready-made aspects in `QueryAspect` come in two groups: aspects that apply a data source aspect to all data sources in a query, and one that runs code around the query itself.

### Applying Data Source Aspects to a Query

Data sources are usually created in one place and used from many queries, so it is often easier to apply an aspect to a query than to every data source. `QueryAspect.fromDataSourceAspect` turns a `DataSourceAspect` into a `QueryAspect`. It is implemented with `ZQuery#mapDataSources`, which you can also call directly. The aspect is applied to every data source that the query uses:

```scala
val query: ZQuery[Any, Nothing, List[String]] =
  ZQuery.foreachPar(List(1, 2, 3))(userName)
```

```scala
show(query @@ QueryAspect.fromDataSourceAspect(DataSourceAspect.maxBatchSize(2)))
// result: List(user-1, user-2, user-3)
// UserDataSource: [1, 2] [3]
```

`QueryAspect` also has shortcuts for the two data source aspects from the previous section:

| Query aspect                         | Equivalent                                                           |
| ------------------------------------ | -------------------------------------------------------------------- |
| `QueryAspect.maxBatchSize(n)`        | `QueryAspect.fromDataSourceAspect(DataSourceAspect.maxBatchSize(n))` |
| `QueryAspect.aroundDataSource(b)(a)` | `QueryAspect.fromDataSourceAspect(DataSourceAspect.around(b)(a))`    |

```scala
show(query @@ QueryAspect.maxBatchSize(2))
// result: List(user-1, user-2, user-3)
// UserDataSource: [1, 2] [3]
```

```scala
show(query @@ QueryAspect.aroundDataSource(logBefore)(logAfter))
// result: List(user-1, user-2, user-3)
// before
// UserDataSource: [1, 2, 3]
// after
```

### Running Code Around a Query

`QueryAspect.around` runs an effect before the query and an effect after it, using `ZQuery.acquireReleaseWith`. Unlike `aroundDataSource`, it wraps the query as a whole, not the calls to data sources, and its arguments are plain effects that do not need a description:

```scala
val logQuery: QueryAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
  QueryAspect.around(log.update(_ :+ "query started"))(_ => log.update(_ :+ "query finished"))
```

```scala
show(query @@ logQuery)
// result: List(user-1, user-2, user-3)
// query started
// UserDataSource: [1, 2, 3]
// query finished
```

### Composing Query Aspects

Like data source aspects, query aspects are composed with `>>>` (or `andThen`), and the left aspect is applied first:

```scala
show(query @@ (logQuery >>> QueryAspect.maxBatchSize(2)))
// result: List(user-1, user-2, user-3)
// query started
// UserDataSource: [1, 2] [3]
// query finished
```

## Summary

| To...                                      | Use                                                                                                                       |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| Run code around calls to a data source     | `DataSourceAspect.around`, applied with `DataSource#@@`                                                                   |
| Limit the batch size of a data source      | `DataSourceAspect.maxBatchSize` or `DataSource#batchN`                                                                    |
| Apply these to all data sources in a query | `QueryAspect.aroundDataSource`, `QueryAspect.maxBatchSize`, `QueryAspect.fromDataSourceAspect` or `ZQuery#mapDataSources` |
| Run code around a whole query              | `QueryAspect.around`, applied with `ZQuery#@@`                                                                            |
| Combine aspects                            | `>>>` or `andThen`                                                                                                        |
