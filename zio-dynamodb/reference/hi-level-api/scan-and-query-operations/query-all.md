# queryAll

> ```scala
  def queryAll[A: Schema](
    tableName: String
  ): DynamoDBQuery[A, Stream[Throwable, A]] = ???
```

```scala
  def queryAll[A: Schema](
    tableName: String
  ): DynamoDBQuery[A, Stream[Throwable, A]] = ???
```

The `queryAll` operation must be used in conjunction with the `whereKey` combinator which allows you to specify the partition key and optionally sort key expression the query.

```scala
for {
  _          <- put(tableName, Equipment("1", 2020, "Widget1", 1.0)).execute
  _          <- put(tableName, Equipment("1", 2021, "Widget1", 2.0)).execute
  stream     <- queryAll[Equipment](tableName)
                  .whereKey(Equipment.id.partitionKey === "1" && Equipment.year.sortKey > 2020)
                  .execute
} yield ()
```

## Combinators

```scala
<SCAN_ALL_QUERY>
  .consistency(<ConsistencyMode>)
  .whereKey(<KeyConditionExpr>)  // eg Equipment.id.partitionKey === "1" && Equipment.year.sortKey > 2020
  .filter(<ConditionExpression>) // eg Equipment.price > 1.0 - filtering is done server side AFTER the scan
  .sortOrder(ascending)          // ascending is a boolean flag 
  .indexName(<IndexName>)        // use a secondary index    
```
