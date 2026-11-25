# Optional, Default and Null Fields

> Decide how ZIO JSON treats missing, null and empty fields: Option, default values, explicit nulls, empty collections and extra fields.

Goal: control what happens when a JSON field is missing, `null`, empty or unexpected.

Step 1 describes the default behavior. Each later step is a separate option. Use the ones you need.

## What you need

Every example on this page uses one import:

```scala
import zio.json._
```

## 1. Know the default behavior

When the decoder does not find a field, it picks the result by the type of the field:

| Field type                          | Missing field decodes to                        |
|-------------------------------------|-------------------------------------------------|
| `Option[A]`                         | `None`                                          |
| Field with a default value          | The default value                               |
| Collection such as `List` or `Map`  | An error, unless `decoding = false` (section 3) |
| Anything else                       | An error: `missing`                             |

A field with a default value also takes its default when the JSON value is `null`. The default expression is evaluated again on every decode, so a default such as `UUID.randomUUID()` gives a fresh value each time. Defaults work the same way on Scala 2 and Scala 3, with no extra compiler options:

```scala
case class Profile(name: String, nickname: Option[String], age: Int = 18)

implicit val profileCodec: JsonCodec[Profile] = DeriveJsonCodec.gen[Profile]
```

A missing `nickname` becomes `None`, and a missing or `null` `age` becomes `18`:

```scala
"""{"name":"Ann"}""".fromJson[Profile]
// res1: Either[String, Profile] = Right(
//   value = Profile(name = "Ann", nickname = None, age = 18)
// )
"""{"name":"Ann","age":null}""".fromJson[Profile]
// res2: Either[String, Profile] = Right(
//   value = Profile(name = "Ann", nickname = None, age = 18)
// )
```

A missing field that is not an `Option` and has no default is an error:

```scala
"""{"nickname":"A"}""".fromJson[Profile]
// res3: Either[String, Profile] = Left(value = ".name(missing)")
```

When encoding, `None` is left out of the output by default:

```scala
println(Profile("Ann", None).toJson)
// {"name":"Ann","age":18}
```

## 2. Write nulls with `@jsonExplicitNull`

Put `@jsonExplicitNull` on a case class to write `None` as `null`. Put it on a single field to do that for that field only. When decoding, an `Option` field accepts both `null` and a missing key, whether or not the annotation is present. `Mango` writes `null` for all its `Option` fields, and `Papaya` only for `ripeness`:

```scala
@jsonExplicitNull
case class Mango(ripeness: Option[Int])

object Mango {
  implicit val codec: JsonCodec[Mango] = DeriveJsonCodec.gen[Mango]
}

case class Papaya(@jsonExplicitNull ripeness: Option[Int], color: Option[String])

object Papaya {
  implicit val codec: JsonCodec[Papaya] = DeriveJsonCodec.gen[Papaya]
}
```

A `None` with the annotation becomes `null`, one without it is left out, and a missing key still decodes to `None`:

```scala
println(Mango(None).toJson)
// {"ripeness":null}
println(Papaya(None, None).toJson)
// {"ripeness":null}
"""{}""".fromJson[Mango]
// res7: Either[String, Mango] = Right(value = Mango(ripeness = None))
```

The program-wide form is `JsonCodecConfiguration(explicitNulls = true)`. It is `false` by default.

## 3. Omit empty collections with `@jsonExplicitEmptyCollections`

By default, an empty collection is written as `[]` or `{}`, and the decoder requires the key to be present. The annotation turns each direction off separately:

```scala
@jsonExplicitEmptyCollections(encoding = false, decoding = false)
case class Pineapple(leaves: List[String])

object Pineapple {
  implicit val codec: JsonCodec[Pineapple] = DeriveJsonCodec.gen[Pineapple]
}
```

Now an empty list is left out, and a missing key decodes to an empty list:

```scala
println(Pineapple(Nil).toJson)
// {}
"""{}""".fromJson[Pineapple]
// res9: Either[String, Pineapple] = Right(value = Pineapple(leaves = List()))
```

You can mix the two flags, for example `@jsonExplicitEmptyCollections(encoding = true, decoding = false)`. On a field, the annotation changes encoding only. Decoding reads the annotation on the class.

The program-wide form is `JsonCodecConfiguration(explicitEmptyCollections = ExplicitEmptyCollections(encoding = false, decoding = false))`. Both flags are `true` by default.

## 4. Reject unexpected fields with `@jsonNoExtraFields`

By default, the decoder ignores fields it does not know. Put `@jsonNoExtraFields` on a case class to make them an error:

```scala
@jsonNoExtraFields
case class Watermelon(pips: Int)

object Watermelon {
  implicit val decoder: JsonDecoder[Watermelon] = DeriveJsonDecoder.gen[Watermelon]
}
```

A known field decodes, and an unknown one is rejected:

```scala
"""{"pips":32}""".fromJson[Watermelon]
// res10: Either[String, Watermelon] = Right(value = Watermelon(pips = 32))
"""{"pips":32,"color":"yellow"}""".fromJson[Watermelon]
// res11: Either[String, Watermelon] = Left(value = "(invalid extra field)")
```

The program-wide form is `JsonCodecConfiguration(allowExtraFields = false)`. The annotation does not combine with `@jsonDiscriminator`; see [Sealed traits](sealed-traits.md).

A case class with no fields, or a case object, is a special case. Its decoder accepts any JSON value, not only an object, and skips it. With `@jsonNoExtraFields`, it accepts only `{}`:

```scala
case class Ping()

implicit val pingDecoder: JsonDecoder[Ping] = DeriveJsonDecoder.gen[Ping]

@jsonNoExtraFields
case class StrictPing()

implicit val strictPingDecoder: JsonDecoder[StrictPing] = DeriveJsonDecoder.gen[StrictPing]
```

A number decodes to `Ping()`, while `StrictPing` rejects it:

```scala
"42".fromJson[Ping]
// res12: Either[String, Ping] = Right(value = Ping())
"""{"anything":true}""".fromJson[Ping]
// res13: Either[String, Ping] = Right(value = Ping())
"42".fromJson[StrictPing]
// res14: Either[String, StrictPing] = Left(value = "(expected '{' got '4')")
"{}".fromJson[StrictPing]
// res15: Either[String, StrictPing] = Right(value = StrictPing())
```

## 5. Keep a field out of the JSON with `@jsonExclude`

Put `@jsonExclude` on a field to leave it out of the encoded JSON. Use it for values that stay in memory, such as secrets or cached data. To keep a session token out of the JSON:

```scala
case class Session(user: String, @jsonExclude token: String)

object Session {
  implicit val codec: JsonCodec[Session] = DeriveJsonCodec.gen[Session]
}
```

The encoder skips the field:

```scala
println(Session("alice", "secret").toJson)
// {"user":"alice"}
```

The decoder does not skip it. If the JSON has the field, the value is decoded. If not, decoding fails, as for any other missing field:

```scala
"""{"user":"alice","token":"abc"}""".fromJson[Session]
// res17: Either[String, Session] = Right(
//   value = Session(user = "alice", token = "abc")
// )
"""{"user":"alice"}""".fromJson[Session]
// res18: Either[String, Session] = Left(value = ".token(missing)")
```

Give the field a default value to make it optional when decoding:

```scala
case class SessionWithDefault(user: String, @jsonExclude token: String = "")

implicit val sessionWithDefaultCodec: JsonCodec[SessionWithDefault] =
  DeriveJsonCodec.gen[SessionWithDefault]
```

A missing token now takes the default:

```scala
"""{"user":"alice"}""".fromJson[SessionWithDefault]
// res19: Either[String, SessionWithDefault] = Right(
//   value = SessionWithDefault(user = "alice", token = "")
// )
```

## 6. Keep round trips in mind

The rules on this page mean that some values do not survive an encode followed by a decode. Two cases are worth knowing before you choose a field type.

An `Option` field whose default is not `None` never decodes to `None`. The encoder leaves `None` out (or writes `null` with `@jsonExplicitNull`), and the decoder replaces both a missing key and `null` with the default:

```scala
case class Retry(limit: Option[Int] = Some(3))

implicit val retryCodec: JsonCodec[Retry] = DeriveJsonCodec.gen[Retry]
```

`Retry(None)` is written as `{}` and read back as the default:

```scala
println(Retry(None).toJson)
// {}
Retry(None).toJson.fromJson[Retry]
// res21: Either[String, Retry] = Right(value = Retry(limit = Some(value = 3)))
```

If `None` must mean something different from the default, use `None` as the default, or a type of your own that can say "not set".

A nested `Option[Option[A]]` cannot tell an absent value from an explicit `null` when encoding. Both `None` and `Some(None)` count as "nothing", so both are left out, or both are written as `null` with `@jsonExplicitNull`. On decoding, a missing key and `null` both give `None`:

```scala
case class Patch(nickname: Option[Option[String]])

implicit val patchCodec: JsonCodec[Patch] = DeriveJsonCodec.gen[Patch]
```

`None` and `Some(None)` produce the same JSON, and `null` reads back as `None`:

```scala
println(Patch(None).toJson)
// {}
println(Patch(Some(None)).toJson)
// {}
"""{"nickname":null}""".fromJson[Patch]
// res24: Either[String, Patch] = Right(value = Patch(nickname = None))
```

To tell "absent", "null" and "set" apart, decode the object to the [`Json` AST](../reference/json-ast.md) and check the keys yourself.

## See also

- [Annotations](../reference/annotations.md) lists every derivation annotation in one table.
- [JsonDecoder: Missing Fields](../reference/json-decoder.md#missing-fields) describes how each decoder handles a missing field.
- [Field names](field-names.md) covers `@jsonField` and `@jsonAliases`.
