# Scan and Query Operations

> The High Level API mirrors the Scan and Query operations of DDB but with a high level of type safety.

The High Level API mirrors the Scan and Query operations of DDB but with a high level of type safety. 

We have to start of with a Scala model of the table and add an implicit schema reference in the companion object together with some convenience `ProjectionExpression`'s via the `accessors` function. 

```scala
// year is the sort key
final case class Equipment(id: String, year: Int, name: String, price: Double)
object Equipment {
  implicit val schema: Schema.CaseClass4[String, Int, String, Double, Equipment] = DeriveSchema.gen[Equipment]
  val (id, year, name, price)                                                    = ProjectionExpression.accessors[Equipment]
}
```

A summary is shown below and detailed sections can be found for each operation.

```scala
object DynamoDBQuery { 
  // Scan/Query operations

  def scanAll[A: Schema](
    tableName: String
  ): DynamoDBQuery[A, Stream[Throwable, A]] = ???

  def scanSome[A: Schema](
    tableName: String,
    limit: Int
  ): DynamoDBQuery[A, (Chunk[A], LastEvaluatedKey)] = ???  

  def queryAll[A: Schema](
    tableName: String
  ): DynamoDBQuery[A, Stream[Throwable, A]] = ???

  def querySome[A: Schema](
    tableName: String,
    limit: Int
  ): DynamoDBQuery[A, (Chunk[A], LastEvaluatedKey)] = ???

}
```
