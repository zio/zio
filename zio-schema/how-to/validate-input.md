# How to Validate Untrusted Input

> Decode untrusted input with a codec, check it with Schema.validate, and turn the ValidationErrors into an error response.

In this guide, we accept a sign-up request as JSON, reject it when it breaks the rules of our model, and answer with an error response that lists every problem by field. The rules live on the case class as `@validate` annotations, so the same rules apply wherever the model is decoded. We start by declaring the rules, then decode and validate, group the errors by field, and finally render them as a JSON error body. The last section lists the cases where a rule is silently skipped, and what to do instead.

## The Problem

Data that arrives from outside, such as an HTTP request body, a message from a queue, or a row from a CSV file, can hold any value its type allows. A `String` username can be empty, and an `Int` age can be negative. Decoding only proves that the input has the right shape; it says nothing about whether the values make sense.

Without a library, every model gets a hand-written check that repeats the rules and the error messages:

```scala
final case class Signup(username: String, email: String, age: Int)

def checkSignup(signup: Signup): List[String] = {
  val usernameErrors =
    if (signup.username.length < 3) List("username: must be at least 3 characters")
    else if (signup.username.length > 20) List("username: must be at most 20 characters")
    else Nil
  val emailErrors =
    if (!signup.email.contains("@")) List("email: has an invalid format") else Nil
  val ageErrors =
    if (signup.age <= 17) List("age: must be greater than 17") else Nil
  usernameErrors ++ emailErrors ++ ageErrors
}
```

The check sits apart from the model, so adding a field without updating the check goes unnoticed, and every nested type needs its own check wired in by hand. With ZIO Schema, the rules sit on the fields themselves, and one call checks a value and everything nested inside it.

## Prerequisites

We need the core module for `Schema` and validation, and the JSON module for decoding. The JSON module already depends on `zio-schema-derivation`, which provides `DeriveSchema.gen`:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema"      % "1.9.2",
  "dev.zio" %% "zio-schema-json" % "1.9.2"
)
```

On Scala 2, schema derivation also needs `scala-reflect` at compile time, as the [Installation](../index.md#installation) section shows. This guide assumes you know how to derive a schema; the [Validation](../operations/validating-types.md) reference describes every rule and error type in detail.

## Declare the Rules on the Model

A rule is a `Validation[A]` value, and the `@validate` annotation attaches it to a field. When `DeriveSchema.gen` builds the schema, it stores the rule of each field in the schema. Several annotations on one field, or rules combined with `&&`, must all pass.

Our sign-up request has a username with a length limit, an email address, a minimum age, an optional US phone number, and a nested postal address. `Validation.email` and `PhoneNumberValidation.phoneNumberUS` are predefined validators, and `Validation#optional` lifts a rule for `String` to a rule for `Option[String]` that accepts `None`:

```scala
import zio.Chunk
import zio.schema._
import zio.schema.annotation.validate
import zio.schema.codec.JsonCodec
import zio.schema.validation._

final case class Address(
  @validate(Validation.minLength(1)) street: String,
  @validate(Validation.minLength(1)) city: String
)

object Address {
  implicit val schema: Schema[Address] = DeriveSchema.gen[Address]
}

final case class Signup(
  @validate(Validation.minLength(3) && Validation.maxLength(20)) username: String,
  @validate(Validation.email) email: String,
  @validate(Validation.greaterThan(17)) age: Int,
  @validate(PhoneNumberValidation.phoneNumberUS.optional()) phone: Option[String],
  address: Address
)

object Signup {
  implicit val schema: Schema[Signup] = DeriveSchema.gen[Signup]
}
```

`Address` has its own rules. `Signup` does not repeat them; checking a `Signup` also checks its address. The [Predefined Validators](../operations/validating-types.md#predefined-validators) section lists the other ready-made rules, such as `Validation.uuidV4`, `Validation.ipV4`, and `Validation.time`.

## Decode, Then Validate

Decoding and validating are two separate steps. First we decode a request whose username is too short, whose age is too low, and whose street is empty:

```scala
val json =
  """{"username":"jd","email":"jd@example.com","age":15,"address":{"street":"","city":"Springfield"}}"""
// json: String = "{\"username\":\"jd\",\"email\":\"jd@example.com\",\"age\":15,\"address\":{\"street\":\"\",\"city\":\"Springfield\"}}"

val decoded = JsonCodec.jsonDecoder(Signup.schema).decodeJson(json)
// decoded: Either[String, Signup] = Right(
//   value = Signup(
//     username = "jd",
//     email = "jd@example.com",
//     age = 15,
//     phone = None,
//     address = Address(street = "", city = "Springfield")
//   )
// )
```

The decoder returns `Right` even though three fields break their rules.

:::caution[Codecs Do Not Validate]
No codec in ZIO Schema runs validation rules when it decodes. A value that breaks its `@validate` rules decodes successfully. Always call `Schema.validate` on the decoded value before you trust it.
:::

`Schema.validate` walks the value with its schema and returns every failed check as a `ValidationError`. An empty `Chunk` means the value is valid:

```scala
decoded.map(signup => Schema.validate(signup).map(_.message))
// res1: Either[String, Chunk[String]] = Right(
//   value = IndexedSeq(
//     "Expected the length of  to be at least 1 characters but was 0 characters.",
//     "Expected the length of jd to be at least 3 characters but was 2 characters.",
//     "15 should be greater than 17"
//   )
// )
```

The walk descends into nested records, the elements of collections, and the content of `Option` and `Either`, so the empty street of the nested `Address` shows up without extra code.

`Schema.validate(value)` and its method form, `Schema#validate`, both check with the implicit `Schema[A]` in scope. When you hold a schema that is not the implicit one, pass it explicitly: `Schema.validate(value)(otherSchema)`.

## Group Errors by Field

A `ValidationError` describes what failed, such as a string that is too short, but it does not say which field it came from. A client needs the field name to show the error next to the right input. The schema knows the fields, so we can walk them ourselves: for each field of the record, `Schema.Field#get` reads the value, `Schema.Field#validation` checks the field's own rule, and `Schema.validate` with `Schema.Field#schema` checks everything nested inside the field:

```scala
def checkField[R, A](field: Schema.Field[R, A], record: R): Chunk[ValidationError] = {
  val value = field.get(record)
  val own   = field.validation.validate(value).swap.getOrElse(Chunk.empty)
  own ++ Schema.validate(value)(field.schema)
}

def fieldErrors[A](value: A)(implicit schema: Schema[A]): Map[String, Chunk[ValidationError]] =
  schema match {
    case record: Schema.Record[A] =>
      record.fields
        .map(field => field.fieldName -> checkField(field, value))
        .filter(_._2.nonEmpty)
        .toMap
    case _ =>
      val errors = Schema.validate(value)
      if (errors.isEmpty) Map.empty else Map("value" -> errors)
  }
```

`Schema.Field#fieldName` is the name the codecs use, so it honours a `@fieldName` annotation. Applied to our decoded request, the errors now carry their field names:

```scala
decoded.map(fieldErrors(_))
// res2: Either[String, Map[String, Chunk[ValidationError]]] = Right(
//   value = Map(
//     "username" -> IndexedSeq(MinLength(minLength = 3, actualLength = 2, string = "jd")),
//     "age" -> IndexedSeq(GreaterThan(value = 15, expected = 17)),
//     "address" -> IndexedSeq(MinLength(minLength = 1, actualLength = 0, string = ""))
//   )
// )
```

An error inside the nested address is reported under `address`, the top-level field that contains it. To report `address.street` instead, call `fieldErrors` again on the nested value.

## Turn Errors into an Error Response

The client should get a stable, readable error body, not the raw `ValidationError` values. We translate each error into a short message by matching on the error cases, and describe the response itself with a case class, so its schema gives us a JSON encoder for free:

```scala
def describe(error: ValidationError): String =
  error match {
    case ValidationError.MinLength(min, _, _)     => s"must be at least $min characters"
    case ValidationError.MaxLength(max, _, _)     => s"must be at most $max characters"
    case ValidationError.GreaterThan(_, expected) => s"must be greater than $expected"
    case ValidationError.LessThan(_, expected)    => s"must be less than $expected"
    case ValidationError.RegexMatch(_, _)         => "has an invalid format"
    case other                                    => other.message
  }

final case class ErrorResponse(error: String, fields: Map[String, List[String]])

object ErrorResponse {
  implicit val schema: Schema[ErrorResponse] = DeriveSchema.gen[ErrorResponse]
}

val errorCodec = JsonCodec.jsonCodec(ErrorResponse.schema)
```

The `RegexMatch` case matters for rules built from a regular expression, such as `Validation.email`: its own message prints the whole regular expression, which a client cannot use.

Next, one function decodes the body, validates the result, and picks the response. A body that does not decode becomes a `malformed_body` error, and a body that decodes but breaks a rule becomes a `validation_failed` error:

```scala
def parseSignup(body: String): Either[ErrorResponse, Signup] =
  JsonCodec.jsonDecoder(Signup.schema).decodeJson(body) match {
    case Left(message) =>
      Left(ErrorResponse("malformed_body", Map("body" -> List(message))))
    case Right(signup) =>
      val errors = fieldErrors(signup)
      if (errors.isEmpty) Right(signup)
      else
        Left(
          ErrorResponse(
            "validation_failed",
            errors.map { case (name, problems) => name -> problems.map(describe).toList }
          )
        )
  }

def respond(body: String): String =
  parseSignup(body) match {
    case Left(response) => s"400 ${errorCodec.encodeJson(response, None)}"
    case Right(signup)  => s"201 created ${signup.username}"
  }
```

The `respond` function stands in for an HTTP handler. We send it our invalid request, a request that lacks required fields, and a valid request:

```scala
println(respond(json))
// 400 {"error":"validation_failed","fields":{"username":["must be at least 3 characters"],"age":["must be greater than 17"],"address":["must be at least 1 characters"]}}

println(respond("""{"username":"jane"}"""))
// 400 {"error":"malformed_body","fields":{"body":[".email(missing)"]}}

println(respond("""{"username":"jane_doe","email":"jane@example.com","age":30,"address":{"street":"1 Main St","city":"Springfield"}}"""))
// 201 created jane_doe
```

The same `ErrorResponse` type works for any model, because `fieldErrors` only needs a schema. If you build an HTTP API with ZIO HTTP, its `Endpoint` API runs `Schema.validate` on request bodies for you; [How to Build a JSON HTTP API with ZIO HTTP](json-http-api.md) shows how to shape its error responses.

## Rules That Are Silently Skipped

Validation fails open in a few cases: the rule exists, but `Schema.validate` reports no error. Each case below has a working alternative, and the [Validation](../operations/validating-types.md) reference shows each one with a runnable example.

### Rules Added with `Schema#validation`

`Schema#validation(v)` stores the rule `v` as an annotation on the schema, but `Schema.validate` only reads the rules of record fields, so it never checks this one. [Rules on a Whole Schema](../operations/validating-types.md#rules-on-a-whole-schema) shows a value that breaks such a rule and still passes.

:::caution[`Schema#validation` Is Not Checked]
`Schema.validate` ignores a rule added with `Schema#validation`. Put the rule on a record field with `@validate` (or the `validation0` parameter of `Schema.Field.apply` in a hand-written schema), or call `Validation#validate` on the value directly.
:::

### Rules That Fail Without an Error

`Validation.fail` always fails, but it fails with an empty list of errors. `Schema.validate` turns that empty list into "no errors", so a field annotated with `@validate(Validation.fail[Int])` passes. The same happens with the default `onLeft` of `Validation#right` and the default `onRight` of `Validation#left`, which are `Validation.fail`. [Constant Rules](../operations/validating-types.md#constant-rules) shows the effect.

:::caution[A Rule That Fails with No Error Reports Nothing]
Through `@validate`, a rule that fails without an error is reported as valid. Check such a rule with `Validation#validate` and test for `Left`, instead of relying on an empty `Schema.validate` result.
:::

### Case Classes with More Than 22 Fields on Scala 2

On Scala 2, `DeriveSchema.gen` represents a case class with more than 22 fields as a `GenericRecord` and drops every `@validate` annotation of that class. Scala 3 derivation keeps them.

:::caution[Scala 2 Drops `@validate` Above 22 Fields]
On Scala 2, `Schema.validate` reports no errors for the fields of a case class with more than 22 fields. Move the validated fields into a nested case class with 22 fields or fewer, whose rules `Schema.validate` checks as part of the parent, or call `Validation#validate` on those field values directly.
:::

## Going Further

The [Validation](../operations/validating-types.md) reference covers every rule constructor, the boolean combinators, the regular-expression DSL, and all `ValidationError` cases. [Validating Types](../operations/validating-types.md) gives a shorter overview of validation, and [Schema Annotations](../schema-annotations.md) lists the other annotations that shape decoding, such as `@fieldName` and `@fieldDefaultValue`. On Scala 3, [How to Use ZIO Schema with Scala 3](scala-3.md) shows how to derive the same schemas with `derives Schema`.
