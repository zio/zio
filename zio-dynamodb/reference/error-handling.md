# Error Handling

> DynamoDBError is a sealed trait that represents all the possible errors that can occur when interacting with DynamoDB
and is returned by the DynamoDBQuery `execute` method.

DynamoDBError is a sealed trait that represents all the possible errors that can occur when interacting with DynamoDB
and is returned by the DynamoDBQuery `execute` method.

```scala
def execute: ZIO[DynamoDBExecutor, DynamoDBError, Out] = ???
```

The error hierarchy is as follows:

- **`DynamoDBError`** top level sealed trait
  - **`DynamoDBError.ItemError`** sealed trait for item level errors
    - **`DynamoDBError.ItemError.ValueNotFound`** - returned by `get` in the High Level API. If you expect items to be missing you can use the `maybeFound` extension method to return `None` in this scenario. see [Working With `get` Return Values](hi-level-api/crud-operations/get#working-with-get-return-values) for more details.  
    - **`DynamoDBError.ItemError.DecodingError`** - returned by automatic codecs in the High Level API when data does not match the expected type described by the schema
  - **`DynamoDBError.AWSError`** - case class that contains the underlying AWS `Exception`. Typically, you need to pattern match on this when you expect a specific AWS exception eg AWS `ConditionalCheckFailedException` when you do a strict insert operation by having a condition expression that asserts the primary key does not exist.
  - **`DynamoDBError.BatchError`** sealed trait for batch related errors. You need to consider this error if queries result in batching eg if you are using `DynamoDBQuery.batch` or manually `Zip`'ing together `DynamoDBQuery`'. Note at the point that this error is raised automatic retries have already occurred. For a long running process typical handler actions would be to record the errors and to carry on processing. See [Auto batching and parallelisation](auto-batching-and-parallelisation) section for more details.
    - **`DynamoDBError.BatchError.GetError`** - case class returned by automatic batching - `unprocessedKeys` contains a Map of table name to primary key   
    - **`DynamoDBError.BatchError.WriteError`** - returned by automatic batching - `unprocessedItems` contains a map of table name to item/primary key 
    - **`DynamoDBError.BatchError.UnbatchableQueryError`** - returned on execute of a query created by the `batch` function - see [Auto batching and parallelisation](auto-batching-and-parallelisation) section for more details. The error message will contain a detailed explanation.
    
  - **`DynamoDBError.TransactionError`** sealed trait for transaction related errors. You need to handle this error if you are using the transaction API ie `<dynamoDBQuery>.transaction` or `<dynamoDBQuery>.safeTransaction`
    - **`DynamoDBError.TransactionError.EmptyTransaction`**
    - **`DynamoDBError.TransactionError.MixedTransactionTypes`**
    - **`DynamoDBError.TransactionError.InvalidTransactionActions`**
  - **`DynamoDBError.UnknownNonFatalError`** Encapsulates a non-fatal error (as defined by scala.utils.control.NonFatal) that occurred during processing
