# DataSource Reference

> Reference for building and transforming a DataSource: the fromFunction family, Batched, make, never, combinators and the ZQuery constructors that use them.

A `DataSource[R, A]` is the component of ZIO Query that talks to the outside world: it receives requests of type `A`, may use an environment `R`, and returns a `CompletedRequestMap` with one result per request. [Creating Data Sources](./creating-data-sources.md) walks through writing one by hand. This page lists every constructor and combinator, so you can pick the shortest one for your case. For how the requests of a query end up in batches, see [How Batching, Pipelining, and Parallelism Work](./how-batching-works.md).

The shape of the type is:

```scala
trait DataSource[-R, -A] {
  val identifier: String
  def runAll(requests: Chunk[Chunk[A]])(implicit trace: Trace): ZIO[R, Nothing, CompletedRequestMap]
}
```

The outer `Chunk` holds batches that must run one after another, and each inner `Chunk` holds requests that can run in parallel. A data source must return a result for every request it receives, otherwise the query dies with a `QueryFailure`. Most code does not implement `runAll` directly: the constructors below build the data source from a plain function.

## Usage

The following block defines a request type and a data source with `DataSource.fromFunctionBatchedZIO`, then queries it with four parallel lookups. The data source prints the size of each batch it receives, so we can see that the four lookups reach it as one call, and that the duplicate request is deduplicated:

```scala
import zio._
import zio.query._

case class GetUserName(id: Int) extends Request[Nothing, String]

val users: DataSource[Any, GetUserName] =
  DataSource.fromFunctionBatchedZIO[Any, Nothing, GetUserName, String]("users") { requests =>
    Console.printLine(s"users received ${requests.size} requests").orDie.as(requests.map(r => s"user-${r.id}"))
  }

def runSync[A](query: ZQuery[Any, Nothing, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(query.run).getOrThrowFiberFailure()
  }
```

Now we query the data source with ids `1`, `2`, `2` and `3`:

```scala
runSync(ZQuery.foreachPar(List(1, 2, 2, 3))(id => ZQuery.fromRequest(GetUserName(id))(users)))
// res1: List[String] = List("user-1", "user-2", "user-2", "user-3")
```

The data source received three requests, not four: identical requests (compared with `equals`, so use case classes) are sent once per query run, because the results are cached by default.

## Choosing a Constructor

All constructors take a `name` first. The name becomes the data source's `DataSource#identifier`, and it must be unique per data source, see [Identifier and Equality](#identifier-and-equality). The following table lists the constructors by what the function you provide receives and returns:

| Constructor                                        | Your function                                 | Effectful | Can skip a request |
| -------------------------------------------------- | --------------------------------------------- | --------- | ------------------ |
| `DataSource.fromFunction`                          | `A => B`                                      | No        | No                 |
| `DataSource.fromFunctionZIO`                       | `A => ZIO[R, E, B]`                           | Yes       | No                 |
| `DataSource.fromFunctionOption`                    | `A => Option[B]`                              | No        | Yes                |
| `DataSource.fromFunctionOptionZIO`                 | `A => ZIO[R, E, Option[B]]`                   | Yes       | Yes                |
| `DataSource.fromFunctionBatched`                   | `Chunk[A] => Chunk[B]`                        | No        | No                 |
| `DataSource.fromFunctionBatchedZIO`                | `Chunk[A] => ZIO[R, E, Chunk[B]]`             | Yes       | No                 |
| `DataSource.fromFunctionBatchedOption`             | `Chunk[A] => Chunk[Option[B]]`                | No        | Yes                |
| `DataSource.fromFunctionBatchedOptionZIO`          | `Chunk[A] => ZIO[R, E, Chunk[Option[B]]]`     | Yes       | Yes                |
| `DataSource.fromFunctionBatchedWith`               | `Chunk[A] => Chunk[B]` and `B => Request`     | No        | No                 |
| `DataSource.fromFunctionBatchedWithZIO`            | `Chunk[A] => ZIO[R, E, Chunk[B]]` and `B => Request` | Yes | No                 |
| `DataSource.Batched.make`                          | `Chunk[A] => ZIO[R, Nothing, CompletedRequestMap]` | Yes  | Yes                |
| `DataSource.make`                                  | `Chunk[Chunk[A]] => ZIO[R, Nothing, CompletedRequestMap]` | Yes | Yes         |

Use the single-request functions (`DataSource.fromFunction`, `DataSource.fromFunctionZIO`, `DataSource.fromFunctionOption`, `DataSource.fromFunctionOptionZIO`) for tests and for backends that have no bulk operation. Use the batched functions when the backend can answer many requests in one call, which is the case ZIO Query exists to optimize. Use `DataSource.Batched.make` or `DataSource.make` when you need to build the `CompletedRequestMap` yourself.

In the signatures below, `A` is the request type and `B` is the result type. The implicit evidence `A <:< Request[E, B]` ties them together, so the type parameters are normally spelled out on the constructor, as in the examples.

## Creating Data Sources

The constructors are grouped by how they receive requests: one at a time, as a batch, or as raw batches of batches.

### Functions of One Request

`DataSource.fromFunction`, `DataSource.fromFunctionZIO`, `DataSource.fromFunctionOption` and `DataSource.fromFunctionOptionZIO` call your function once per request. The effectful variants run the calls with `ZIO.foreachPar`, so the requests of one batch are processed in parallel. The `Option` variants model lookups that may find nothing:

```scala
object DataSource {
  def fromFunction[A, B](name: String)(f: A => B)(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A]
  def fromFunctionZIO[R, E, A, B](name: String)(f: A => ZIO[R, E, B])(implicit ev: A <:< Request[E, B]): DataSource[R, A]
  def fromFunctionOption[A, B](name: String)(f: A => Option[B])(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A]
  def fromFunctionOptionZIO[R, E, A, B](name: String)(f: A => ZIO[R, E, Option[B]])(implicit ev: A <:< Request[E, B]): DataSource[R, A]
}
```

A failing effect fails only its own request: the error becomes that request's result, and the other requests in the batch still succeed. A `None` leaves the request without a result, and a query that needs it dies with a `QueryFailure`, so use the `Option` variants only when a missing entry is a bug in the data source. The following block builds a pure data source and an effectful one that can fail:

```scala
case class User(id: Int, name: String)
case class GetUser(id: Int) extends Request[String, User]

val pure: DataSource[Any, GetUserName] =
  DataSource.fromFunction[GetUserName, String]("pure")(request => s"user-${request.id}")

val effectful: DataSource[Any, GetUser] =
  DataSource.fromFunctionZIO[Any, String, GetUser, User]("effectful") { request =>
    if (request.id > 0) ZIO.succeed(User(request.id, s"user-${request.id}"))
    else ZIO.fail(s"invalid id ${request.id}")
  }
```

Pick `DataSource.fromFunction` for tests and stubs: it needs no environment and no effects. Pick `DataSource.fromFunctionZIO` when each request needs a call to a service, for example an HTTP endpoint with no bulk variant. These constructors do not combine requests, so a database lookup per request still costs one round trip per request.

### Functions of a Batch of Requests

`DataSource.fromFunctionBatched` and its variants hand your function the whole batch as a `Chunk[A]` and expect a result for every request. Results are matched to requests by position, so your function must return a chunk of the same size and in the same order. If the result is shorter, the requests without a result die with a `QueryFailure`. The four variants differ in whether the function is effectful and whether it may return `None` for a request:

```scala
object DataSource {
  def fromFunctionBatched[A, B](name: String)(f: Chunk[A] => Chunk[B])(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A]
  def fromFunctionBatchedZIO[R, E, A, B](name: String)(f: Chunk[A] => ZIO[R, E, Chunk[B]])(implicit ev: A <:< Request[E, B]): DataSource[R, A]
  def fromFunctionBatchedOption[A, B](name: String)(f: Chunk[A] => Chunk[Option[B]])(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A]
  def fromFunctionBatchedOptionZIO[R, E, A, B](name: String)(f: Chunk[A] => ZIO[R, E, Chunk[Option[B]]])(implicit ev: A <:< Request[E, B]): DataSource[R, A]
}
```

If the effect of an effectful variant fails, every request in that batch fails with the same cause. The next block shows the typical use: one bulk query for all ids, with the result for an unknown id left out:

```scala
val knownUsers: Map[Int, String] = Map(1 -> "Ada", 2 -> "Grace")

val bulk: DataSource[Any, GetUserName] =
  DataSource.fromFunctionBatchedOption[GetUserName, String]("bulk") { requests =>
    requests.map(request => knownUsers.get(request.id))
  }
```

Pick these constructors when the backend has a bulk operation such as `SELECT ... WHERE id IN (...)` and returns results in request order. For the SQL case, where rows come back in arbitrary order, use the `With` variants below.

### Functions That Return Results in Any Order

`DataSource.fromFunctionBatchedWith` and `DataSource.fromFunctionBatchedWithZIO` take a second function that maps each result back to the request it answers. Your batch function may then return results in any order, and no longer has to return one per request:

```scala
object DataSource {
  def fromFunctionBatchedWith[A, B](name: String)(f: Chunk[A] => Chunk[B], g: B => Request[Nothing, B])(implicit ev: A <:< Request[Nothing, B]): DataSource[Any, A]
  def fromFunctionBatchedWithZIO[R, E, A, B](name: String)(f: Chunk[A] => ZIO[R, E, Chunk[B]], g: B => Request[E, B])(implicit ev: A <:< Request[E, B]): DataSource[R, A]
}
```

Here `g` takes a result of type `B` and returns the request that produced it. The following block answers `FindUser` requests with a list of users in reverse order, and `g` rebuilds each request from the user's id:

```scala
case class FindUser(id: Int) extends Request[Nothing, User]

val unordered: DataSource[Any, FindUser] =
  DataSource.fromFunctionBatchedWith[FindUser, User]("unordered")(
    requests => requests.map(request => User(request.id, s"user-${request.id}")).reverse,
    user => FindUser(user.id)
  )
```

Pick the `With` variants when the backend returns rows keyed by id rather than in request order. The effectful variant fails every request of the batch if the effect fails.

### Building a Batched Data Source by Hand

`DataSource.Batched` is a trait for data sources that process each batch of parallel requests in one call, and `DataSource.Batched.make` builds one from a function. Your function returns a `CompletedRequestMap` (see [CompletedRequestMap](./completed-request-map.md) for how to build one), so it controls the result of every request, including failures and omissions. You rarely need the trait directly: prefer the `fromFunction*` constructors, and fall back to `Batched.make` when they do not fit, for example when only some requests in a batch fail:

```scala
object DataSource {
  trait Batched[-R, -A] extends DataSource[R, A] {
    def run(requests: Chunk[A])(implicit trace: Trace): ZIO[R, Nothing, CompletedRequestMap]
    final def runAll(requests: Chunk[Chunk[A]])(implicit trace: Trace): ZIO[R, Nothing, CompletedRequestMap]
  }

  object Batched {
    def make[R, A](name: String)(f: Chunk[A] => ZIO[R, Nothing, CompletedRequestMap]): DataSource[R, A]
  }
}
```

`Batched` implements `runAll` for you: it calls `run` once per inner batch, in order, and skips requests that an earlier batch already completed. The next block builds a data source with `Batched.make` that answers known ids and fails the rest:

```scala
val manual: DataSource[Any, GetUser] =
  DataSource.Batched.make[Any, GetUser]("manual") { requests =>
    ZIO.succeed(
      CompletedRequestMap.fromIterableWith(requests)(
        request => request,
        request =>
          knownUsers.get(request.id) match {
            case Some(name) => Exit.succeed(User(request.id, name))
            case None       => Exit.fail(s"unknown user ${request.id}")
          }
      )
    )
  }
```

### Introspecting Sequential Batches

`DataSource.make` builds a data source from `runAll` itself. The function receives the `Chunk[Chunk[A]]` described above, so it sees the batches that must run sequentially and can optimize across them, for example by sending one query for all of them. Because it must produce the `CompletedRequestMap` itself, use it only when you need that view; `Batched` is enough otherwise:

```scala
object DataSource {
  def make[R, A](name: String)(f: Chunk[Chunk[A]] => ZIO[R, Nothing, CompletedRequestMap]): DataSource[R, A]
}
```

The following block flattens all batches into a single call:

```scala
val flattened: DataSource[Any, GetUserName] =
  DataSource.make[Any, GetUserName]("flattened") { batches =>
    ZIO.succeed(
      CompletedRequestMap.fromIterableWith(batches.flatten)(
        request => request,
        request => Exit.succeed(s"user-${request.id}")
      )
    )
  }
```

### A Data Source That Never Answers

`DataSource.never` is a `DataSource[Any, Any]` whose `runAll` never completes. Because of the contravariant type parameter, it can be used for any request type. It is useful in tests of timeouts, races and interruption:

```scala
object DataSource {
  val never: DataSource[Any, Any]
}
```

The next block queries it with a request, so the query never completes unless it is timed out or raced:

```scala
val hanging: ZQuery[Any, Nothing, String] =
  ZQuery.fromRequest(GetUserName(1))(DataSource.never)
```

## Transforming Data Sources

The following methods on `DataSource` return a new data source. Each one derives the new `DataSource#identifier` from the original, so data sources built with the same combinators and the same descriptions are equal. Functions passed to `contramap`, `contramapZIO`, `eitherWith` and `provideSomeEnvironment` are wrapped in `Described`, a pair of a value and a string description that ZIO Query uses to build the identifier (see [Aspects](./aspects.md#describing-values-with-described)). Wrap a function with `Described(f, "description")`, or with `f ? "description"` after `import zio.query.Described._`. Equal functions need the same description, and different functions need different ones.

### Limiting the Batch Size

`batchN(n)` splits each batch into groups of at most `n` requests, which the underlying data source then receives one group after another. Use it when the backend limits the number of items per call, for example an SQL `IN` clause or an API that accepts up to 100 ids. A value of `n` below `1` makes the data source die with an `IllegalArgumentException` when it runs:

```scala
trait DataSource[-R, -A] {
  def batchN(n: Int): DataSource[R, A]
}
```

To cap the data source at two requests per call:

```scala
val capped: DataSource[Any, GetUserName] = users.batchN(2)
```

### Changing the Request Type

`contramap` and `contramapZIO` return a data source for requests of type `B`, by converting each `B` request into an `A` request first. `contramapZIO` takes an effectful conversion that cannot fail, and runs the conversions of a batch in parallel:

```scala
trait DataSource[-R, -A] {
  final def contramap[B](f: Described[B => A]): DataSource[R, B]
  final def contramapZIO[R1 <: R, B](f: Described[B => ZIO[R1, Nothing, A]]): DataSource[R1, B]
}
```

:::warning[The Result Is Keyed by the Converted Request]
The new data source returns the `CompletedRequestMap` of the original data source as is. A query looks up its result with the request it submitted, so the converted request must be equal to the submitted one, otherwise the query dies with a `QueryFailure`. Use `contramap` to normalize or enrich a request in a way that keeps it equal, for example to fill in a field.
:::

The following block shows the `?` syntax with a conversion that returns an equal request:

```scala
import zio.query.Described._

val same: DataSource[Any, GetUserName] =
  users.contramap(((request: GetUserName) => request) ? "same")
```

### Routing Requests to Two Data Sources

`eitherWith` combines two data sources into one for a request type `C`. The function sorts every request into `Left` for this data source or `Right` for the other one, and each side receives its own requests in parallel. The results of both are merged:

```scala
trait DataSource[-R, -A] {
  final def eitherWith[R1 <: R, B, C](that: DataSource[R1, B])(f: Described[C => Either[A, B]]): DataSource[R1, C]
}
```

Use it to route one request hierarchy to two backends. As with `contramap`, the request returned by `f` must equal the request it came from, which holds when `A` and `B` are subtypes of `C`. The following block routes the requests of a sealed hierarchy to separate data sources:

```scala
sealed trait UserRequest                  extends Request[Nothing, String]
case class ByCache(id: Int)               extends UserRequest
case class ByDatabase(id: Int)            extends UserRequest

val cache: DataSource[Any, ByCache] =
  DataSource.fromFunction[ByCache, String]("cache")(request => s"cached-${request.id}")

val database: DataSource[Any, ByDatabase] =
  DataSource.fromFunction[ByDatabase, String]("database")(request => s"stored-${request.id}")

val routed: DataSource[Any, UserRequest] =
  cache.eitherWith(database)(Described[UserRequest => Either[ByCache, ByDatabase]]({
    case request: ByCache    => Left(request)
    case request: ByDatabase => Right(request)
  }, "byType"))
```

### Racing Two Data Sources

`race` sends the same requests to both data sources and returns the results of the first one that completes, interrupting the other safely. Use it for hedging between a fast and a slow replica:

```scala
trait DataSource[-R, -A] {
  final def race[R1 <: R, A1 <: A](that: DataSource[R1, A1]): DataSource[R1, A1]
}
```

The next block races a data source against `DataSource.never`, which always loses:

```scala
val raced: DataSource[Any, GetUserName] = users.race(DataSource.never)
```

### Providing the Environment

`provideEnvironment` removes the whole environment requirement of a data source, and `provideSomeEnvironment` replaces it with a smaller one by mapping the new environment to the original. The environment is passed as a `Described` value, in the same way as the functions above:

```scala
trait DataSource[-R, -A] {
  final def provideEnvironment(r: Described[ZEnvironment[R]]): DataSource[Any, A]
  final def provideSomeEnvironment[R0](f: Described[ZEnvironment[R0] => ZEnvironment[R]]): DataSource[R0, A]
}
```

The following block defines a data source that needs a `Prefix` service and provides it, so the result needs no environment:

```scala
case class Prefix(value: String)

val prefixed: DataSource[Prefix, GetUserName] =
  DataSource.fromFunctionZIO[Prefix, Nothing, GetUserName, String]("prefixed") { request =>
    ZIO.serviceWith[Prefix](prefix => s"${prefix.value}${request.id}")
  }

val ready: DataSource[Any, GetUserName] =
  prefixed.provideEnvironment(Described(ZEnvironment(Prefix("user-")), "Prefix(user-)"))
```

### Identifier and Equality

`DataSource#identifier` is the name given to the constructor, and two data sources are equal when their identifiers are equal. `ZQuery` uses this equality to group requests: requests to equal data sources are batched together, so use one unique identifier per logical data source and do not reuse it for another one. `toString` returns the identifier.

## Submitting Requests

A `DataSource` is used through `ZQuery`. The constructors on `ZQuery` return a query that sends requests to the data source, and the optimizations of ZIO Query apply only to queries built with them.

### Single Requests

`ZQuery.fromRequest` takes a request and a data source, both by name, and returns a query for the result. `DataSource#query` does the same from the data source side. The request is looked up in the cache first, so a repeated request within one run reaches the data source only once. `ZQuery.fromRequestUncached` skips the cache, so every occurrence reaches the data source:

```scala
object ZQuery {
  def fromRequest[R, E, A, B](request: => A)(dataSource: => DataSource[R, A])(implicit ev: A <:< Request[E, B]): ZQuery[R, E, B]
  def fromRequestUncached[R, E, A, B](request: => A)(dataSource: => DataSource[R, A])(implicit ev: A <:< Request[E, B]): ZQuery[R, E, B]
}

trait DataSource[-R, -A] {
  final def query[E, A1 <: A, B](request: A1)(implicit ev: A1 <:< Request[E, B]): ZQuery[R, E, B]
}
```

To submit a request in the three ways:

```scala
val viaFromRequest: ZQuery[Any, Nothing, String] = ZQuery.fromRequest(GetUserName(1))(users)
val viaQuery: ZQuery[Any, Nothing, String]       = users.query(GetUserName(1))
val viaUncached: ZQuery[Any, Nothing, String]    = ZQuery.fromRequestUncached(GetUserName(1))(users)
```

Use `ZQuery.fromRequestUncached` for requests whose answer may change during a run, such as reading a counter. Requests that are not cached are also not deduplicated.

### Many Requests at Once

`ZQuery.fromRequests` submits a `Chunk` or a `List` of requests, and `DataSource#queryAll` does the same for a `Chunk`. The results come back in the order of the requests. All the requests are submitted together, so they reach the data source in one batch, without the cost of one query per element. `ZQuery.fromRequestsWith` takes values and a function that turns each value into a request, which saves building the intermediate collection:

```scala
object ZQuery {
  def fromRequests[R, E, A, B](requests: Chunk[A])(dataSource: DataSource[R, A])(implicit ev: A <:< Request[E, B]): ZQuery[R, E, Chunk[B]]
  def fromRequests[R, E, A, B](requests: List[A])(dataSource: DataSource[R, A])(implicit ev: A <:< Request[E, B]): ZQuery[R, E, List[B]]
  def fromRequestsWith[R, E, In, A, B](as: Chunk[In], f: In => A)(dataSource: DataSource[R, A])(implicit ev: A <:< Request[E, B]): ZQuery[R, E, Chunk[B]]
  def fromRequestsWith[R, E, In, A, B](as: List[In], f: In => A)(dataSource: DataSource[R, A])(implicit ev: A <:< Request[E, B]): ZQuery[R, E, List[B]]
}

trait DataSource[-R, -A] {
  final def queryAll[E, A1 <: A, B](requests: Chunk[A1])(implicit ev: A1 <:< Request[E, B]): ZQuery[R, E, Chunk[B]]
}
```

The following block fetches the names for a list of ids in one query:

```scala
runSync(ZQuery.fromRequestsWith(List(1, 2, 3), GetUserName(_))(users))
// res3: List[String] = List("user-1", "user-2", "user-3")
```

When you already have all the requests, prefer `ZQuery.fromRequests` and `ZQuery.fromRequestsWith` to combining one `ZQuery.fromRequest` query per element with `ZQuery.foreachPar`, because they avoid building a query per request.
