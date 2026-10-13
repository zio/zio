# Cats Effect Interop

> The **`zio-dynamodb-ce`** cats effect interop module provides a way to use ZIO DynamoDB with Cats Effect 3 with minimal 
> effort.

## Usage

Add the following line to your `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-dynamodb-ce" % "1.0.0-RC24"
)
```

The entry points are the `DynamoDBExecutorF.of` and `DynamoDBExecutorF.ofCustomised` constructors which provide a `Resource` managed
`DynamoDBExecutorF` instance. Once we have this instance in implicit scope we can use the extension method `executeToF` 
to run the queries. Queries that would normally return a `ZIO` effect now return a `F` effect, and queries that would
normally return a `ZStream` now return an FS2 `Stream`.

## Example

```scala
package zio.dynamodb.examples.dynamodblocal.interop

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import zio.dynamodb.DynamoDBQuery.{ createTable, deleteTable, get, put }

import cats.effect.std.Console
import cats.effect.IO
import cats.effect.IOApp
import cats.syntax.all._

import java.net.URI

import zio.dynamodb.interop.ce.syntax._
import zio.dynamodb.ProjectionExpression
import zio.schema.DeriveSchema
import zio.schema.Schema
import zio.dynamodb.KeySchema
import zio.dynamodb.BillingMode
import zio.dynamodb.AttributeDefinition
import zio.dynamodb.DynamoDBQuery
import cats.effect.kernel.Async

/**
 * example cats effect interop application
 *
 * to run in the sbt console:
 * {{{
 * zio-dynamodb-examples/runMain zio.dynamodb.examples.dynamodblocal.interop.CeInteropExample
 * }}}
 */
object CeInteropExample extends IOApp.Simple {

  final case class Person(id: String, name: String)
  object Person {
    implicit val schema: Schema.CaseClass2[String, String, Person] = DeriveSchema.gen[Person]
    val (id, name)                                                 = ProjectionExpression.accessors[Person]
  }

  def program[F[_]](implicit F: Async[F]) = {
    val console = Console.make[F]

    for {
      _ <- DynamoDBExceutorF
             .ofCustomised[F] { builder => // note only AWS SDK model is exposed here, not zio.aws
               builder
                 .endpointOverride(URI.create("http://localhost:8000"))
                 .region(Region.US_EAST_1)
                 .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
             }
             .use { implicit dynamoDBExecutorF => // To use extension method "executeToF" we need implicit here
               for {
                 _         <- createTable("Person", KeySchema("id"), BillingMode.PayPerRequest)(
                                AttributeDefinition.attrDefnString("id")
                              ).executeToF
                 _         <- put(tableName = "Person", Person(id = "avi", name = "Avinder")).executeToF
                 result    <- get(tableName = "Person")(Person.id.partitionKey === "avi").executeToF
                 _         <- console.println(s"found=$result")
                 fs2Stream <- DynamoDBQuery
                                .scanAll[Person](tableName = "Person")
                                .parallel(50) // server side parallel scan
                                .filter(Person.name.beginsWith("Avi") && Person.name.contains("de"))
                                .executeToF
                 _         <- fs2Stream.evalTap(person => console.println(s"person=$person")).compile.drain
                 _         <- deleteTable("Person").executeToF
               } yield ()
             }
    } yield ()
  }

  val run = program[IO]
}
```
