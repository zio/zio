# Creating Data Sources

> To construct a `ZQuery` that executes a request, you first need to create a `DataSource`. A `DataSource[R, A]` requires an environment `R` and is capable of executing requests of type `A`. It is defined in terms of:

To construct a `ZQuery` that executes a request, you first need to create a `DataSource`. A `DataSource[R, A]` requires an environment `R` and is capable of executing requests of type `A`. It is defined in terms of:

- an `identifier` that uniquely identifies the data source
- an effectual function `runAll` from a `Chunk[Chunk[A]]` of requests to a `CompletedRequestMap` of requests and results

The outer `Chunk` represents batches of requests that must be performed sequentially. The inner `Chunk` represents a batch of requests that can be performed in parallel. This allows data sources to introspect on all the requests being executed and optimize the query.

Let's consider `getUserNameById` from the previous example.

We need to define a corresponding request type that extends `Request` for a given response type:

```scala
case class GetUserName(id: Int) extends Request[Throwable, String]
```

Now let's build the corresponding `DataSource`. We will create a `Batched` data source that executes requests that can be performed in parallel in batches but does not further optimize batches of requests that must be performed sequentially. We need to implement the following functions:

```scala
lazy val UserDataSource = new DataSource.Batched[Any, GetUserName] {
  val identifier: String = ???
  def run(requests: Chunk[GetUserName])(implicit trace: Trace): ZIO[Any, Nothing, CompletedRequestMap] = ???
}
```

We will use "UserDataSource" as our identifier. This name should not be reused for other data sources.

```scala
val identifier: String = "UserDataSource"
```

We will define two different behaviors depending on whether we receive a single request or multiple requests at once. For each request, we need to insert into the result map a value of type `Exit` (`fail` for an error and `succeed` for a success).

```scala
def run(requests: Chunk[GetUserName]): ZIO[Any, Nothing, CompletedRequestMap] =
  requests.toList match {
    case request :: Nil =>
      // get user by ID e.g. SELECT name FROM users WHERE id = $id
      val result: Task[String] = ???
      result.exit.map(CompletedRequestMap.single(request, _))
    case batch =>
      // get multiple users at once e.g. SELECT id, name FROM users WHERE id IN ($ids)
      val result: Task[List[(Int, String)]] = ???
      result.foldCause(
        CompletedRequestMap.failCause(requests, _),
        CompletedRequestMap.fromIterableWith(_)(kv => GetUserName(kv._1), kv => Exit.succeed(kv._2))
      )
  }
```

Now to build a `ZQuery`, we can use `ZQuery.fromRequest` and just pass the request and the data source:

```scala
def getUserNameById(id: Int): ZQuery[Any, Throwable, String] =
  ZQuery.fromRequest(GetUserName(id))(UserDataSource)
```

To run a `ZQuery`, simply use `ZQuery#run` which will return a `ZIO[R, E, A]`.
