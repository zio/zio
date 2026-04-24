# Future Interop

> The **`zio-dynamodb-future`** interop module provides a way to use ZIO DynamoDB with vanilla scala Futures with minimal 
effort.

The **`zio-dynamodb-future`** interop module provides a way to use ZIO DynamoDB with vanilla scala Futures with minimal 
effort.

## Usage

Add the following line to your `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-dynamodb-future" % "1.0.0-RC24"
)
```

The entry point is `DynamoDBExecutorF.make` which allows the user to customise the DynamoDB client and is placed in implicit scope. 
Rather that using the normal `execute` which would return a `ZIO` effect, we import a syntax class `import zio.dynamodb.interop.future.syntax._`
which allows us to use the extension method `executeToF` to run the queries and via interop return a `Future`. At the end we use the `DynamoDBExecutorF.close()` method to release the underlying resources.

## Example

```scala
package zio.dynamodb.examples.dynamodblocal.interop

object FutureInteropExample extends App {
  implicit val ddbExec: DynamoDBExecutorF = DynamoDBExecutorF.make(
    buildNettyClient = identity,
    buildDynamoDbClient = _.endpointOverride(java.net.URI.create("http://localhost:8000"))
      .region(software.amazon.awssdk.regions.Region.US_EAST_1)
      .credentialsProvider(
        software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
          software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("dummy", "dummy")
        )
      )
  )

  val program          = for {
    _      <- DynamoDBQuery
                .createTable("Person", KeySchema("id"), BillingMode.PayPerRequest)(
                  AttributeDefinition.attrDefnString("id")
                )
                .executeToF
    _      <- put(tableName = "Person", Person(id = "avi", name = "Avinder")).executeToF
    result <- get(tableName = "Person")(Person.id.partitionKey === "avi").executeToF
    _       = println(s"found=$result")
    _      <- DynamoDBQuery.deleteTable("Person").executeToF

  } yield ()
  val programWithClose = program.andThen { case _ => ddbExec.close() }

  Await.result(programWithClose, 30.seconds)
}
```
