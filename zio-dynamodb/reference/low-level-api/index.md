# Low Level API

> Low Level API operations are found on the `DynamoDBQuery` companion object. All the function names contain the word `Item` to indicate that they are operations on AWS DDB [items](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/WorkingWithItems.html) in a table.

Low Level API operations are found on the `DynamoDBQuery` companion object. All the function names contain the word `Item` to indicate that they are operations on AWS DDB [items](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/WorkingWithItems.html) in a table.

```scala
object DynamoDBQuery {

  // CRUD operations  

  def putItem(tableName: String, item: Item): DynamoDBQuery[Any, Option[Item]] = ???

  def getItem(
    tableName: String,
    key: PrimaryKey,
    projections: ProjectionExpression[_, _]*
  ): DynamoDBQuery[Any, Option[Item]] = ???

  def updateItem[A](tableName: String, key: PrimaryKey)(action: Action[A]): DynamoDBQuery[A, Option[Item]] = ???

  def deleteItem(tableName: String, key: PrimaryKey): Write[Any, Option[Item]] = ???

  // Scan/Query operations

  def scanSomeItem(tableName: String, limit: Int, projections: ProjectionExpression[_, _]*): ScanSome = ???

  def scanAllItem(tableName: String, projections: ProjectionExpression[_, _]*): ScanAll = ???

  def queryAllItem(tableName: String, projections: ProjectionExpression[_, _]*): QueryAll = ???

  def querySomeItem(tableName: String, limit: Int, projections: ProjectionExpression[_, _]*): QuerySome = ???

}
```
