# Getting Started

> ```scala
libraryDependencies += "dev.zio" %% "zio-dynamodb" % 1.0.0-RC24
```

## Add the dependency to your build.sbt file

```scala
libraryDependencies += "dev.zio" %% "zio-dynamodb" % 1.0.0-RC24
```

### Read & write data to/from DynamoDB

```scala

object Main extends ZIOAppDefault {

  final case class Person(id: Int, firstName: String)
  object Person {
    implicit lazy val schema: Schema.CaseClass2[Int, String, Person] = DeriveSchema.gen[Person]

    val (id, firstName) = ProjectionExpression.accessors[Person]
  }
  val examplePerson = Person(1, "avi")

  private val program = for {
    _      <- put("personTable", examplePerson).execute
    person <- get("personTable")(Person.id.partitionKey === 1).execute
    _      <- zio.Console.printLine(s"hello $person")
  } yield ()

  override def run =
    program.provide(
      netty.NettyHttpClient.default,
      config.AwsConfig.default, // uses real AWS dynamodb
      dynamodb.DynamoDb.live,
      DynamoDBExecutor.live
    )
}
```
