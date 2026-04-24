# ZIO DynamoDB JSON Module

> **`zio-dynamodb-json`** is a new experimental optional module designed for debugging and troubleshooting purposes.
It renders a representation of the native DynamoDB types and data in a standard JSON format used in AWS console views

**`zio-dynamodb-json`** is a new experimental optional module designed for debugging and troubleshooting purposes.
It renders a representation of the native DynamoDB types and data in a standard JSON format used in AWS console views

It uses the same codecs as the regular library and provides an accurate representation of how the data that would be stored in DynamoDB. 
It works at the level of the AttributeValue type and so works with both the low level and high level APIs.
Note ATM it does not support the Binary and Binary Set types.

Internally the module uses ZIO JSON and in particular it uses the AST which is not used by ZIO Json itself for efficiency reasons,
hence the recommendations is not to use the module in production code.

Some example use cases include:
- visualizing the Attribute Value representation of a case class during model development
- can be used in unit tests to verify that DB mapping is as expected
- production troubleshooting - grabbing DDB JSON from the AWS console in production and decoding it to a case class for debugging

## Usage

In your `build.sbt` file add the following line:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-dynamodb-json" % "1.0.0-RC24"
)
```

Add the following import to your code:

```scala

```

## Methods

Method | Description
--- | ---
**`toJsonString`** // extension method | Converts a case class to a JSON string
**`toJsonStringPretty`** // extension method | Converts a case class to a pretty printed JSON string
**`def parse[A: Schema](jsonString: String): Either[DynamoDBError.ItemError, A]`** | takes a JSON string and returns an Either of an error or an `A` 
**`def parseItem(json: String): Either[DynamoDBError.ItemError, AttrMap]`** | takes a JSON string and returns an Either of an error or an `Item` 

## Visualizing the DB representation of a case class during model development

This can be accomplished by using either the `toJsonString` or `toJsonStringPretty` extension methods on a case class instance - eg:

```scala
final case class Person(
  email: String,
  hobbies: Map[String, List[String]],
  friends: Set[String],
  registrationDate: Instant
)
object Person {
  implicit val schema: Schema.CaseClass4[String, Map[String, List[String]], Set[String], Instant, Person] =
    DeriveSchema.gen[Person]
}
val person = Person("email", Map("sports" -> List("cricket", "football")), Set("John", "Tarlochan"), Instant.now)
println(person.toJsonStringPretty[Person])
```
Console output:
```json
{
  "registrationDate" : {
    "S" : "2024-12-05T06:44:49.011916Z"
  },
  "friends" : {
    "SS" : [
      "John",
      "Tarlochan"
    ]
  },
  "hobbies" : {
    "sports" : {
      "L" : [
        {
          "S" : "cricket"
        },
        {
          "S" : "football"
        }
      ]
    }
  },
  "email" : {
    "S" : "email"
  }
}
```

For a basic introduction to data modelling [Creating Models](reference/hi-level-api/creating-models/index.md) section.

For details on how to customise data mappings please see the [Codec Customisation](guides/codec-customization.md) section.

## Grabbing JSON from the AWS console and decoding it to a case class

This can be useful in a production troubleshooting scenario where you have a JSON representation of a DynamoDB item 
from the AWS console, and you want to decode it to a case class for local testing/debugging purposes.

eg:

```scala
val json             =
    """{
        "registrationDate" : {
          "S" : "2024-12-05T06:44:49.011916Z"
        },
        "friends" : {
          "SS" : [
          "John",
          "Tarlochan"
          ]
        },
        "hobbies" : {
          "sports" : {
          "L" : [
        {
          "S" : "cricket"
        },
        {
          "S" : "football"
        }
          ]
        }
        },
        "email" : {
          "S" : "email"
        }
      }"""
  val errorOrPerson = parse[Person](json) // ZIO DynamoDB Json parse method used here
  println(
    errorOrPerson
  ) // Right(Person("email", Map("sports" -> List("cricket", "football")), Set("John", "Tarlochan"), 2024-12-05T06:44:49.011916Z))
```
