# Error Handling

> A `ZQuery[R, E, A]` can fail with a typed error `E`, die with a defect, or be interrupted, just like a `ZIO` effect. This page shows where those failures come from when a query talks to a data source, what happens to the other requests in the same batch, and which `ZQuery` operators recover from each kind of failure, convert between them, or bound the time a query may take.

## How Errors Flow

Every request type declares its own error type, as in `Request[E, A]`. When a data source completes a request, it stores an `Exit[E, A]` for that request in the `CompletedRequestMap`, so each request succeeds or fails on its own. `ZQuery.fromRequest` turns that `Exit` into the result of the query, which is why the error type of a request becomes the error type of the query.

The effect returned by `DataSource#runAll` has the error type `Nothing`: a data source reports problems for individual requests through the `CompletedRequestMap`, not by failing as a whole. Three situations can therefore make a query fail:

| Situation                                              | Result in the query                      |
| ------------------------------------------------------ | ---------------------------------------- |
| The data source stores `Exit.fail(e)` for a request    | A failure of type `E`                    |
| The data source stores `Exit.die(t)` for a request     | A defect                                 |
| The data source returns no result for a request        | A defect: a [`QueryFailure`](#queryfailure-a-request-without-a-result) |

## Setting Up an Example

The examples on this page use one request type and three data sources. The request type fails with a `String` error. The first data source answers ids `1` and `2`, fails id `3` with a typed error, and fails id `4` with a defect. The second data source forgets to answer id `99`. The third counts how many times it is called.

```scala
import zio._
import zio.query._

final case class GetUser(id: Int) extends Request[String, String]

val userDataSource: DataSource[Any, GetUser] =
  DataSource.make[Any, GetUser]("UserDataSource") { requests =>
    ZIO.succeed(
      CompletedRequestMap.fromIterableWith(requests.flatten)(
        r => r,
        r =>
          r.id match {
            case 3     => Exit.fail("user 3 not found")
            case 4     => Exit.die(new RuntimeException("connection reset"))
            case other => Exit.succeed(s"user-$other")
          }
      )
    )
  }

val forgetfulDataSource: DataSource[Any, GetUser] =
  DataSource.make[Any, GetUser]("ForgetfulDataSource") { requests =>
    ZIO.succeed(
      CompletedRequestMap.fromIterableWith(requests.flatten.filter(_.id != 99))(r => r, r => Exit.succeed(s"user-${r.id}"))
    )
  }

val calls: Ref[Int] = Unsafe.unsafe(implicit unsafe => Ref.unsafe.make(0))

val countingDataSource: DataSource[Any, GetUser] =
  DataSource.make[Any, GetUser]("CountingDataSource") { requests =>
    calls.update(_ + 1) *> ZIO.succeed(
      CompletedRequestMap.fromIterableWith(requests.flatten)(r => r, _ => Exit.fail("service unavailable"))
    )
  }

def user(id: Int): ZQuery[Any, String, String] =
  ZQuery.fromRequest(GetUser(id))(userDataSource)

def run[E, A](query: ZQuery[Any, E, A]): Exit[E, A] =
  Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(query.run.exit).getOrThrowFiberFailure())

def describe[E, A](query: ZQuery[Any, E, A]): String =
  run(query) match {
    case Exit.Success(a) => s"success: $a"
    case Exit.Failure(c) =>
      c.failureOption
        .map(e => s"failure: $e")
        .orElse(c.dieOption.map(t => s"defect: ${t.getClass.getSimpleName}: ${t.getMessage}"))
        .getOrElse("interrupted")
  }
```

The `describe` helper runs a query and renders its outcome in one line, so that the output below stays short.

## Typed Errors and Defects

A request that the data source answers with `Exit.fail` fails the query with that error, and `Exit.die` makes the query die:

```scala
describe(user(1))
// res0: String = "success: user-1"
describe(user(3))
// res1: String = "failure: user 3 not found"
describe(user(4))
// res2: String = "defect: RuntimeException: connection reset"
```

### A Failing Request Does Not Fail Its Batch

The requests in a batch are completed one by one, so a failing request does not change the results of the other requests in the same batch. To see this, we combine three requests with `ZQuery.foreachPar`, which sends them to the data source in one batch, and turn each result into an `Either` with `either`:

```scala
describe(ZQuery.foreachPar(List(1, 2, 3))(id => user(id).either))
// res3: String = "success: List(Right(user-1), Right(user-2), Left(user 3 not found))"
```

Each request has its own result: ids `1` and `2` succeeded and id `3` failed. Without `either`, the first failure fails the whole `foreachPar` query:

```scala
describe(ZQuery.foreachPar(List(1, 2, 3))(user))
// res4: String = "failure: user 3 not found"
```

## QueryFailure: A Request Without a Result

A data source must provide a result for every request it receives. If it leaves a request out of the `CompletedRequestMap`, the query dies with a `QueryFailure`. The defect carries the data source and the request that was not completed, and its message names both:

```scala
val forgotten = ZQuery.fromRequest(GetUser(99))(forgetfulDataSource)
// forgotten: ZQuery[Any, String, String] = zio.query.ZQuery@3c73b6c5

describe(forgotten)
// res5: String = "defect: QueryFailure: Data source ForgetfulDataSource did not complete request GetUser(99)."
```

A `QueryFailure` is a defect, not a typed error, so it does not appear in the error type of the query and `catchAll` does not recover from it. It signals a bug in the data source, not a condition that a caller is expected to handle.

The only operator that is aware of `QueryFailure` is `optional`. It converts a query to one that returns `Some` if the data sources returned results for all requests and `None` otherwise. Typed errors and other defects still fail the query:

```scala
describe(forgotten.optional)
// res6: String = "success: None"
describe(user(1).optional)
// res7: String = "success: Some(user-1)"
describe(user(3).optional)
// res8: String = "failure: user 3 not found"
```

:::note
`DataSource.fromFunctionOption` and `DataSource.fromFunctionOptionZIO` construct data sources that may not provide results for all requests. Use `optional` on queries built from them.
:::

## Recovering from Errors

The operators below follow the naming of `ZIO`. They differ in what they recover from: the `catchAll` family and the `fold`/`foldQuery`/`foldZIO` family handle typed errors only, while the `*Cause` variants also see defects.

| Operator                                  | Handles                  | Recovers with                  |
| ----------------------------------------- | ------------------------ | ------------------------------ |
| `catchAll`                                | Typed errors             | A `ZQuery`                     |
| `catchAllZIO`                             | Typed errors             | A `ZIO` effect                 |
| `catchAllCause`                           | The whole `Cause`        | A `ZQuery`                     |
| `catchAllCauseZIO`                        | The whole `Cause`        | A `ZIO` effect                 |
| `fold`                                    | Typed errors and success | A pure value                   |
| `foldQuery`                               | Typed errors and success | A `ZQuery`                     |
| `foldZIO`                                 | Typed errors and success | A `ZIO` effect                 |
| `foldCauseQuery`                          | The whole `Cause` and success | A `ZQuery`                |
| `foldCauseZIO`                            | The whole `Cause` and success | A `ZIO` effect            |
| `either`                                  | Typed errors and success | `Either[E, A]`, cannot fail    |
| `optional`                                | `QueryFailure`           | `Option[A]`                    |

### Recovering with a Fallback Query

`catchAll` takes a function from the error to a new query. The fallback query is a full query: it can issue requests of its own, and those requests are batched with the rest of the query:

```scala
describe(user(3).catchAll(_ => user(1)))
// res9: String = "success: user-1"
```

`catchAll` handles only typed errors. A defect passes through unchanged:

```scala
describe(user(4).catchAll(_ => user(1)))
// res10: String = "defect: RuntimeException: connection reset"
```

`catchAllCause` receives the whole `Cause`, so it can also recover from defects:

```scala
describe(user(4).catchAllCause(_ => user(1)))
// res11: String = "success: user-1"
```

To recover with an effect instead of a query, use `catchAllZIO` or `catchAllCauseZIO`:

```scala
describe(user(3).catchAllZIO(error => ZIO.succeed(s"fallback after: $error")))
// res12: String = "success: fallback after: user 3 not found"
```

### Handling Both Outcomes

`fold` handles failure and success in one step and produces a query that cannot fail. `foldQuery` and `foldZIO` do the same with a query or an effect, and `foldCauseQuery` and `foldCauseZIO` receive the whole `Cause` for the failure case:

```scala
describe(user(3).fold(error => s"failed: $error", name => s"found: $name"))
// res13: String = "success: failed: user 3 not found"
describe(user(4).foldCauseQuery(cause => ZQuery.succeed(s"died: ${cause.isDie}"), name => ZQuery.succeed(name)))
// res14: String = "success: died: true"
```

`either` is the shortest way to make a failure visible as a value, as in the batch example above.

## Working with the Full Cause

`sandbox` moves the whole `Cause` into the error channel, so that typed errors and defects can be handled in one place with the ordinary operators. `unsandbox` is the inverse:

```scala
val sandboxed: ZQuery[Any, Cause[String], String] = user(4).sandbox
// sandboxed: ZQuery[Any, Cause[String], String] = zio.query.ZQuery@192c64cb

describe(sandboxed.mapError(cause => s"was a defect: ${cause.isDie}"))
// res15: String = "failure: was a defect: true"
describe(ZQuery.unsandbox(sandboxed))
// res16: String = "defect: RuntimeException: connection reset"
```

`sandboxWith` applies a function to the sandboxed query and unsandboxes the result. It is useful for partial recovery from both errors and defects at once:

```scala
describe(user(4).sandboxWith[Any, String, String](_.catchAll(_ => ZQuery.succeed("recovered"))))
// res17: String = "success: recovered"
```

## Converting Between Errors and Defects

`mapError` and `mapErrorCause` transform the error or the whole `Cause` without changing success values. They do not prevent batching of the requests in the query.

`orDie` turns a typed error into a defect. It requires the error type to be a `Throwable`; `orDieWith` takes a function that converts the error:

```scala
describe(user(3).orDieWith(message => new IllegalStateException(message)))
// res18: String = "defect: IllegalStateException: user 3 not found"
```

`refineOrDie` keeps only the errors matched by a partial function and turns all others into defects. `refineOrDieWith` takes a function that converts the unmatched error to a `Throwable`:

```scala
val refined: ZQuery[Any, Int, String] =
  user(3).refineOrDieWith { case message if message.endsWith("not found") => 404 }(new RuntimeException(_))
// refined: ZQuery[Any, Int, String] = zio.query.ZQuery@7f7a8f7d

describe(refined)
// res19: String = "failure: 404"
```

`unrefine`, `unrefineTo` and `unrefineWith` go the other way: they move some defects of the query into the error channel.

## Timeouts

`timeout` returns `None` if the query does not complete within the given duration, and `Some` of the result otherwise. `timeoutFail` and `timeoutFailCause` fail with a given error or `Cause` instead, and `timeoutTo` produces a default value:

```scala
describe(ZQuery.never.timeout(100.millis))
// res20: String = "success: None"
describe(ZQuery.never.timeoutFail("took too long")(100.millis))
// res21: String = "failure: took too long"
describe(user(1).timeout(1.second))
// res22: String = "success: Some(user-1)"
```

A timeout also prevents later requests of a timed-out query from being executed. The test suite `ZQuerySpec` checks that a query that times out while sleeping never sends its next request to the data source.

### Racing Two Queries

`race` runs two queries and returns the result of the first one that succeeds, interrupting the other. If both fail, the failure `Cause` of the result combines the two:

```scala
describe(user(3).race(user(2)))
// res23: String = "success: user-2"
```

## Retrying

`ZQuery` has no `retry` operator. Because every call to `run` starts with its own cache, you can retry a whole query by retrying the effect that `run` returns:

```scala
val retried: ZIO[Any, String, String] =
  ZQuery.fromRequest(GetUser(1))(countingDataSource).run.retry(Schedule.recurs(2))
```

```scala
Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(retried.either).getOrThrowFiberFailure())
// res24: Either[String, String] = Left(value = "service unavailable")

Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(calls.get).getOrThrowFiberFailure())
// res25: Int = 3
```

The data source was called three times: the first attempt and two retries.

:::warning
Within a single `run`, the result of a request is cached, including a failed result. Re-running the same request inside a query, for example with `catchAll(_ => sameQuery)`, returns the cached failure and does not call the data source again. To retry a request inside one run, use `uncached` on the retried part or retry the whole `run` as above.
:::

## Errors and the Environment

A query that requires an environment fails like any other query when providing that environment fails. `provideLayer` builds the layer when the query runs and fails the query with the error of the layer if building it fails. The layer has to be wrapped in a `Described` value, which `?` creates from a description:

```scala
import Described._

val failingLayer: ZLayer[Any, String, Int] = ZLayer.fromZIO(ZIO.fail("no connection"))
// failingLayer: ZLayer[Any, String, Int] = Suspend(
//   self = zio.ZLayer$$$Lambda$13503/0x00007f7ea6193ca0@78eb18c7
// )

val needsInt: ZQuery[Int, Nothing, Int] = ZQuery.service[Int]
// needsInt: ZQuery[Int, Nothing, Int] = zio.query.ZQuery@54f0baa0

describe(needsInt.provideLayer(failingLayer ? "failingLayer"))
// res26: String = "failure: no connection"
```

`provideEnvironment` and `provideSomeEnvironment` also take `Described` values and eliminate the requirement without any chance of failure.
