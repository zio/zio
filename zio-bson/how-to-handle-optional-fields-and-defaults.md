# Handle Optional Fields and Default Values

> Documents in a collection are rarely uniform: older documents lack newer fields, and some fields are only set sometimes. This guide shows how to read and write such documents with `Option` fields, default values and the `skipNullsInCaseClass` setting.

## Problem

## Use Option for Fields That May Be Missing

A field of type `Option[A]` decodes to `None` when the key is missing or the value is `NULL`. When encoding, `None` is left out of the document, because `skipNullsInCaseClass` is `true` by default:

```scala
import zio.bson._
import zio.bson.BsonBuilder._
import zio.bson.magnolia._

final case class User(name: String, nickname: Option[String])

object User {
  implicit val codec: BsonCodec[User] = DeriveBsonCodec.derive
}
```

To see what `User` writes and reads, encode a user without a nickname, then decode documents with the key missing and with `NULL`:

```scala
println(User("Ada", None).toBsonValue)
// {"name": "Ada"}
println(doc("name" -> str("Ada")).as[User])
// Right(User(Ada,None))
println(doc("name" -> str("Ada"), "nickname" -> bNull).as[User])
// Right(User(Ada,None))
```

## Use a Default Value for Documents Written Before a Field Existed

When a case class field has a default value and the key is missing, the decoder uses the default instead of failing. This lets us add a field without migrating old documents:

```scala
final case class Settings(theme: String, pageSize: Int = 20)

object Settings {
  implicit val codec: BsonCodec[Settings] = DeriveBsonCodec.derive
}
```

To decode a document written before `pageSize` existed:

```scala
doc("theme" -> str("dark")).as[Settings]
// res3: Either[String, Settings] = Right(
//   value = Settings(theme = "dark", pageSize = 20)
// )
```

A field without a default and without an `Option` type is required. Its absence is an error with the path of the field:

```scala
doc("pageSize" -> int(50)).as[Settings]
// res4: Either[String, Settings] = Left(value = ".theme: missing")
```

## Write Explicit Nulls

Some consumers expect every key to be present. To write `None` as `NULL` instead of leaving the key out, set `skipNullsInCaseClass` to `false` in the [configuration](./bson-codec-configuration.md):

```scala
final case class Profile(name: String, bio: Option[String])

object Profile {
  implicit val config: BsonCodecConfiguration =
    BsonCodecConfiguration(skipNullsInCaseClass = false)

  implicit val codec: BsonCodec[Profile] = DeriveBsonCodec.derive
}
```

With the setting turned off, `None` is written as `NULL`:

```scala
println(Profile("Ada", None).toBsonValue)
// {"name": "Ada", "bio": null}
```

## Related Pages

- [Configuration and Annotations](./bson-codec-configuration.md) lists every setting of the derivation.
- [Type Mappings](./types.md) describes how `Option` maps to BSON.
