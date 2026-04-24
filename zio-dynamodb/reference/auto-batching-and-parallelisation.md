# Auto batching and parallelisation

> When `DynamoDBQuery`'s are composed manually via the `zip` combinator function they become eligible for auto-batching and parallelisation in the `execute` method.

When `DynamoDBQuery`'s are composed manually via the `zip` combinator function they become eligible for auto-batching and parallelisation in the `execute` method. 

When they are composed automatically using the `batch` function they are eligible for auto-batching but **no parallelisation** occurs.

```scala
val batchedWrite1 = DynamoDBQuery.put("person", Person("1", "John", 21))
        .zip(DynamoDBQuery.put("person", Person("2", "Jane", 22)))

val batchedWrite2 = DynamoDBQuery.batch(people)(person => put("person", person))

for {
  _ <- batchedWrite1.execute // PutItem operations will be batched
  _ <- batchedWrite2.execute // PutItem operations will be batched
} yield ()
```

## Rules for determining auto-batching vs parallelisation behaviour

The rules for determining whether a query is auto-batched are determined by what query types are eligible for batching in the AWS API. The AWS [BatchWriteItem](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchWriteItem.html) operation can only deal with `PutItem` and `DeleteItem` operations. Furthermore, for both of these operations - condition expressions are not allowed. The AWS [BatchGetItem](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_GetItem.html) operation is used for batching `GetItems`'s .

So the rules are as follows: 

- if there are multiple queries `zip`'ed together they are grouped by their type  `GetItem` or `Writes` (`PutItem`/`DeleteItem`) and batched using the `AWS` `BatchGetItem`/`BatchWriteItem` APIs - but _only if_ they pass the below rules:

  - The query is a `PutItem` or `DeleteItem` operation (`put` and `deleteFrom` in the High Level API)
    - The query does not have a condition expression
    - The query has `ReturnValues.None` specified (which is the default) - any other return value will invalidate batched execution.
  - The query is a `GetItem` operation (`get` in the High Level API)
    - The query's `projections` list contains the primary key - this is required to match the response data to the request. Note all fields are included by default so this is only a concern if you explicitly specify the projection expression.  
- for manually `zip`'ed queries, if a query does not qualify for auto-batching it will be parallelised automatically

# Batching using the `batch` function

The `batch(someCollection)(el => someQuery)` function is the preferred way of composing batched queries. Queries are automatically zipped together, however 
on `execute` the parallelisation step above is omitted and instead a `DynamoDBError.BatchError.UnbatchableQueryError` is returned with a detailed error message for each rule violation.         

The use of `batch` the recommended approach for batching queries rather than using `zip`, however you will still have to manually manage the size of the batch manually (see next section).  

## Maximum batch sizes for `BatchWriteItem` and `BatchGetItem`

When using the `zip` or `batch` operations one thing to bear in mind is the maximum number of queries that the `BatchWriteItem` and `BatchGetItem` operations can handle:

- `BatchWriteItem` can handle up to **25** `PutItem` or `DeleteItem` operations
- `BatchGetItem` can handle up to **100** `GetItem` operations

If these are exceeded then you will get a runtime AWS error. For further information please refer to the AWS documentation linked above.

If you want to avoid managing the batch size manually please see the `batchWriteFromStream` and `batchReadFromStream` functions in the section below.

## Automatic retry of unprocessed batch items/keys

Note that both the AWS `BatchWriteItem` and `BatchGetItem` operations return a list of unprocessed items/keys. If this list is non-empty then the operation are retried automatically by the ZIO DynamoDB library. 

If retries do not succeed in eliminating the unprocessed items/keys then the whole batch is failed with a `BatchError.WriteError`/`BatchError.GetError` - both of which will contain a list of the unprocessed items/keys. 

The default retry policy is:

```scala
Schedule.recurs(3) && Schedule.exponential(50.milliseconds)
```

This can be overridden by using the `withRetryPolicy` combinator:

```scala
batchedWrite2.withRetryPolicy(myCustomRetryPolicy).execute
```

## Integrating Batching with ZIO Streams

For examples of how to integrate batching with ZIO Stream please see the utility functions 
[`batchWriteFromStream`](https://github.com/zio/zio-dynamodb/blob/series/2.x/dynamodb/src/main/scala/zio/dynamodb/package.scala#L22-L55) 
and [`batchReadFromStream`](https://github.com/zio/zio-dynamodb/blob/series/2.x/dynamodb/src/main/scala/zio/dynamodb/package.scala#L97-L138) in the `zio.dynamodb` package.
These functions take care of details mentioned above such as managing the maximum batch sizes and can also be used as examples for writing your own custom batched streaming operations.
```scalaa
