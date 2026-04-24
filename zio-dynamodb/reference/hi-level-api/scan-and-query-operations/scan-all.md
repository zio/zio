# scanAll

> ```scala
  def scanAll[A: Schema](
    tableName: String
  ): DynamoDBQuery[A, Stream[Throwable, A]] = ???
```

```scala
  def scanAll[A: Schema](
    tableName: String
  ): DynamoDBQuery[A, Stream[Throwable, A]] = ???
```

The `scanAll` operation is used to scan all items in a table, and uses a ZIO stream to return the results.  

Note that scanning all items in a table can be an expensive operation in terms of elapsed time - to speed things up the 
AWS API offers a parallel/segmented scanning mode which can be invoked in the High Level API using the `parallel` combinator - 
internally it takes care of the details of calling the AWS segmented scan API using a fiber per segment and the results 
are merged back into the results stream in an undetermined order.

```scala
for {
  _          <- put(tableName, Equipment("1", 2020, "Widget1", 1.0)).execute
  _          <- put(tableName, Equipment("1", 2021, "Widget1", 2.0)).execute
  stream     <- scanAll[Equipment](tableName)
                  .whereKey(Equipment.id.partitionKey === "1")
                  .execute
  _ <- stream.tap(equip => ZIO.debug(s"equipment: $equip")).runDrain 
} yield ()
```

## Combinators

```scala
<SCAN_ALL_QUERY>
  .consistency(<ConsistencyMode>)
  .filter(<ConditionExpression>) // eg Equipment.price > 1.0 - filtering is done server side AFTER the scan  
  .parallel(<N>)                 // executes a native DDB parallel scan on the server and merges the results back to the stream
  .indexName(<IndexName>)        // use a secondary index    
```
