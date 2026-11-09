# Creating Queries

> There are several ways to create a `ZQuery`. We've seen `ZQuery.fromRequest`, but you can also:

- create a query from a pure value with `ZQuery.succeed`
- create a query from an effect with `ZQuery.fromZIO`
- combine multiple queries with `ZQuery.collectAllPar` and `ZQuery.foreachPar` and their sequential equivalents `ZQuery.collectAll` and `ZQuery.foreach`

The choice between the parallel and the sequential combinators changes how requests are sent to data sources. See [How Batching, Pipelining, and Parallelism Work](./how-batching-works.md) for details.

If you have a `ZQuery`, you can use:

- `map` and `mapError` to modify the result or error type
- `flatMap`, `zipWith`, or `zipWithPar` to combine it with other queries
- `provide` and `provideSome` to eliminate some or all of its environmental requirements

To execute a query, see [Running Queries and Caching](./running_queries.md).

To recover from failures, convert errors to defects, or limit how long a query may run, see [Error Handling](./error-handling.md).
