# deleteFrom

> ```scala
  def deleteFrom[From: Schema](
    tableName: String
  )(
    primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From]
  ): DynamoDBQuery[Any, Option[From]] = ???
```

```scala
  def deleteFrom[From: Schema](
    tableName: String
  )(
    primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From]
  ): DynamoDBQuery[Any, Option[From]] = ???
```  

The `deleteFrom` operation is used to remove an item from a table. The `primaryKeyExpr` param can be created using the `ProjectionExpression`'s in the companion object for model class: 

```scala
for {
  _ <- DynamoDBQuery.deleteFrom("person")(Person.id.primaryKey === "1").execute
} yield ()
```

### `deleteFrom` query operations

```scala
<DELETE_QUERY>
  .returns(<ReturnValues>) // ReturnValues.AllOld | ReturnValues.None <default>
  .where(<ConditionExpression>)
```
