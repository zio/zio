# CRUD Operations

> The High Level API mirrors the CRUD operations of DDB but with a high level of type safety.

The High Level API mirrors the CRUD operations of DDB but with a high level of type safety. 

We have to start of with a Scala model of the table and add an implicit schema reference in the companion object together with some convenience `ProjectionExpression`'s via the `accessors` function. 

```scala
final case class Person(id: String, name: String, age: Int)
object Person {
  implicit val schema: Schema.CaseClass3[String, String, Int, Person] = DeriveSchema.gen[Person]
  val (id, name, age) = ProjectionExpression.accessors[Person]
}
```

A summary is shown below and detailed sections can be found for each operation.

```scala
object DynamoDBQuery {

  // CRUD operations  

  def put[A: Schema](tableName: String, a: A): DynamoDBQuery[A, Option[A]] = ???

  def get[From: Schema](tableName: String)(
    primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From]
  ): DynamoDBQuery[From, Either[ItemError, From]] = ???

  def update[From: Schema](tableName: String)(primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From])(
      action: Action[From]
    ): DynamoDBQuery[From, Option[From]]  = ???

  def deleteFrom[From: Schema](
    tableName: String
  )(
    primaryKeyExpr: KeyConditionExpr.PrimaryKeyExpr[From]
  ): DynamoDBQuery[Any, Option[From]] = ???

}
```
