# DynamoDBQuery

> When we use the Low or High Level API constructors to create a query we end up with the type `DynamoDBQuery` which is a sealed trait.

When we use the Low or High Level API constructors to create a query we end up with the type `DynamoDBQuery` which is a sealed trait.

One of the primary methods on this trait is `execute` which will run the query and return the result as a ZIO effect. 

```scala
def execute: ZIO[DynamoDBExecutor, DynamoDBError, Out] = ???
```

The `execute` method requires a `DynamoDBExecutor` service in order to execute the query using the lower level ZIO AWS DynamoDB library.

The `execute` method does the following:

- if the query type is a composite (`Zip`) then it will automatically batch or parallelise the queries - see 
[Auto batching and parallelisation](auto-batching-and-parallelisation) for the exact rules
- executes the query using the `DynamoDBExecutor` service, which:
  - converts it to an underlying ZIO AWS DynamoDB query
  - converts the ZIO AWS DynamoDB response back to an `Item` (type alias for an `AttrMap`)

When using the High Level API transformations are done between the Scala model and the `Item` type using the automatically
generated codecs that make use of the `ZIO Schema` in implicit scope.

The next sections cover the surface area exposed by `DynamoDBQuery`.

## `DynamoDBQuery` Combinators and Operations

DynamoDBQuery Combinators | Alias | Description
---|-------|---
map |       | map the result of a query with a function
zip | `<*>`   | combine 2 queries together and returns a tuple - makes the resulting query eligible for automatic batching or parallelisation [see Autobatching and Parallelisation](auto-batching-and-parallelisation) for more details
zipWith |       |does a `zip` and then immediately maps the result with a function
zipLeft | `<*`    | a zip that ignores the result of the right query
zipRight| `*>`    | a zip that ignores the result of the left query

DynamoDBQuery Functions | Description
---|---
`def batch[In, A, B](values: Iterable[A])(body: A => DynamoDBQuery[In, B]): DynamoDBQuery[In, List[B]]`  | `DynamoDB.batch` automates the zipping of queries of the same type using a collection as input. [see Autobatching and Parallelisation](auto-batching-and-parallelisation) for more details.  Note that unprocessed items/keys are retried automatically and if they still fail a `BatchError.WriteError`/`BatchError.GetError` is returned both of which will contain a list of the unprocessed items/keys - see `withRetryPolicy` in the below section for overriding the default retry policy.

DynamoDBQuery Operations | Description
---|---
capacity | sets the ReturnConsumedCapacity. [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_GetItem.html#DDB-GetItem-request-ReturnConsumedCapacity). Note capacity data in the response is ignored by the High Level Api
consistency | sets the `ConsistencyMode` for read operations. Valid values are `Strong`and `Weak`(default) [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_GetItem.html#DDB-GetItem-request-ConsistentRead)  
filter | sets the `FilterExpression` - applies to `ScanSome`, `ScanAll`, `QuerySome`, `QueryAll`. Note the filter is applies **after** the read by DDB so no read units are saved, however latency costs are reduced.
gsi | creates a Global Secondary Index - applies to a `CreateTable` query. [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_CreateTable.html#DDB-CreateTable-request-GlobalSecondaryIndexes)
indexName | sets the local secondary index or global secondary index name - applies to `ScanSome`, `ScanAll`, `QuerySome`, `QueryAll`. [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Scan.html#DDB-Scan-request-IndexName) 
lsi | creates a local Secondary Index - applies to a `CreateTable` query. [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_CreateTable.html#DDB-CreateTable-request-LocalSecondaryIndexes) 
metrics | set `ReturnItemCollectionMetrics`, valid values are `None` (default) and `Size` - applies to PutItem, UpdateItem, Delete, Transaction. Note that metric data in the response is ignored by the High Level API. [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_PutItem.html#DDB-PutItem-request-ReturnItemCollectionMetrics)
parallel(N) | Applies only to `Scan` - sements and runs the query in parallel in DDB and merges the items in the response. N is level of parallelism. [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Scan.html#Scan.ParallelScan). 
returns | sets the `ReturnValues` - applies to `UpdateItem`, `DeleteItem`, `PutItem` (see [Crud Operations](reference/hi-level-api/crud-operations/index.md) reference section for each operation for more details). [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_UpdateItem.html#DDB-UpdateItem-request-ReturnValues)
selectAllAttributes, selectAllProjectedAttributes, selectSpecificAttributes, selectCount | Determines the attributes returned by Scan and Query [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Query.html#DDB-Query-request-Select)    
sortOrder | sets the sort order for `Query`'s [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Query.html#DDB-Query-request-ScanIndexForward) 
startKey |Applies to `Query` and `Scan` and specifies the start key for the query. [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Query.html#DDB-Query-request-ExclusiveStartKey)
transaction | executes the query in a transaction - see [Transactions Guide](../guides/transactions) for more details.
where | sets the `ConditionExpression` - applies to `PutItem`, `DeleteItem`, `UpdateItem` and `Scan` [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.OperatorsAndFunctions.html)
whereKey | set the `KeyConditionExpr` applies to `QuerySome` and `QueryAll`. [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_Query.html#DDB-Query-request-KeyConditionExpression) 
withClientRequestToken | set the client request token` - applies to write transactions [AWS API](https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_TransactWriteItems.html#DDB-TransactWriteItems-request-ClientRequestToken)
withRetryPolicy | override the default retry policy for a batched query - [see Autobatching and Parallelisation](auto-batching-and-parallelisation) for more details
