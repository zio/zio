# High Level API

> High Level API constructors for CRUD operations are found on the `DynamoDBQuery` companion object. They rely on a 
ZIO Schema for a particular type being in implicit scope. This is achieved using the `DeriveSchema.gen` macro. 
Internally codecs are automatically generated for the case classes based on the meta-data provided by the `Schema`'s.

High Level API constructors for CRUD operations are found on the `DynamoDBQuery` companion object. They rely on a 
ZIO Schema for a particular type being in implicit scope. This is achieved using the `DeriveSchema.gen` macro. 
Internally codecs are automatically generated for the case classes based on the meta-data provided by the `Schema`'s.

```scala
object DynamoDBQuery {

  // CRUD operation constructors  

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

Once we have constructed  a type safe query we can execute it.

Methods that need a primary key expression take a `KeyConditionExpr.PrimaryKeyExpr[From]` as an argument, however 
rather than create one directly we can create one using a `ProjectionExpression` as a springboard
