# Getting Started

> Learn ZIO JSON step by step: install it, encode and decode case classes, handle optional fields and sealed traits, and read errors.

On this page you will install ZIO JSON, turn Scala values into JSON, read JSON back into Scala values, and handle bad input. Every step builds on the one before it, so run the steps in order.

By the end you will have a small user-profile model: a `Profile` that holds a `Person` with an `Address`, an optional nickname, a list of tags, and a list of contacts of different kinds. You will encode it, decode it, and read the errors it reports for bad input.

## 1. Install

Add the library to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-json" % "1.1.3"
```

:::note[Scala.js and Scala Native]
ZIO JSON is published for the JVM, Scala.js and Scala Native, for Scala 2.12, 2.13 and 3. In a Scala.js or Scala Native project, use `%%%` instead of `%%`.

On those two platforms the `java.time` types come from the `scala-java-time` library. If you use `java.time.ZoneId` or `java.time.ZonedDateTime` there, also add the time-zone database in the same version as the `scala-java-time` in your dependency tree (`sbt dependencyTree` shows it), for example:

```scala
libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.10.0"
```

The add-on modules (YAML, golden testing, `@jsonDerive` macros and the integrations) are listed in the [Modules](index.md#modules) section of the Introduction page, with their platforms.
:::

The code on this page uses Scala 2.13 syntax. Where Scala 3 is shorter, a Scala 3 tip follows the example.

Every snippet below assumes this import:

```scala
import zio.json._
```

The import brings in the type classes `JsonEncoder` and `JsonDecoder`, the derivation helpers, and the extension methods `toJson` and `fromJson`.

## 2. Encode Your First Value

A *codec* is a pair of one encoder (Scala to JSON) and one decoder (JSON to Scala). The easiest way to get one for a case class is to let the compiler derive it. Put the codec on the companion object so Scala finds it without any import:

```scala
final case class Address(street: String, city: String)

object Address {
  implicit val codec: JsonCodec[Address] = DeriveJsonCodec.gen[Address]
}
```

Now call `toJson` on a value:

```scala
val home = Address("1 Main St", "Springfield")
// home: Address = Address(street = "1 Main St", city = "Springfield")

println(home.toJson)
// {"street":"1 Main St","city":"Springfield"}
```

Use `toJsonPretty` when you want indented output for people to read:

```scala
println(home.toJsonPretty)
// {
//   "street" : "1 Main St",
//   "city" : "Springfield"
// }
```

:::tip[Scala 3]
On Scala 3 you can skip the companion and write `derives JsonCodec` on the class instead:

```scala
final case class Address(street: String, city: String) derives JsonCodec
```
:::

## 3. Decode Your First Value

`fromJson` goes the other way. It does not throw. It returns an `Either[String, A]`: a `Right` with the value, or a `Left` with an error message:

```scala
"""{"street":"1 Main St","city":"Springfield"}""".fromJson[Address]
// res4: Either[String, Address] = Right(
//   value = Address(street = "1 Main St", city = "Springfield")
// )
```

The same codec supplies the decoder, because a `JsonCodec` holds both halves.

## 4. Nest One Case Class in Another

A `Person` holds the `Address` from step 2. A case class field can be any type that has a codec, and `Address` got its codec in step 2:

```scala
final case class Person(name: String, age: Int, address: Address)

object Person {
  implicit val codec: JsonCodec[Person] = DeriveJsonCodec.gen[Person]
}
```

The nested value becomes a nested JSON object:

```scala
val alice = Person("Alice", 30, home)
// alice: Person = Person(
//   name = "Alice",
//   age = 30,
//   address = Address(street = "1 Main St", city = "Springfield")
// )

println(alice.toJsonPretty)
// {
//   "name" : "Alice",
//   "age" : 30,
//   "address" : {
//     "street" : "1 Main St",
//     "city" : "Springfield"
//   }
// }
```

Decoding the same text gives the original value back:

```scala
alice.toJson.fromJson[Person]
// res6: Either[String, Person] = Right(
//   value = Person(
//     name = "Alice",
//     age = 30,
//     address = Address(street = "1 Main St", city = "Springfield")
//   )
// )
```

## 5. Model Alternatives with a Sealed Trait

A person can be reached by email or by phone. A `sealed trait` models "one of several shapes". Derive the codec on the trait itself, and its subtypes are derived along with it:

```scala
sealed trait Contact

object Contact {
  final case class Email(address: String) extends Contact
  final case class Phone(number: String)  extends Contact

  implicit val codec: JsonCodec[Contact] = DeriveJsonCodec.gen[Contact]
}
```

By default the JSON names the subtype in a single wrapper key. This key is the *tag* that tells the decoder which subtype to build:

```scala
val contacts: List[Contact] = List(Contact.Email("alice@example.com"), Contact.Phone("555-0100"))
// contacts: List[Contact] = List(
//   Email(address = "alice@example.com"),
//   Phone(number = "555-0100")
// )

println(contacts.toJson)
// [{"Email":{"address":"alice@example.com"}},{"Phone":{"number":"555-0100"}}]

"""[{"Phone":{"number":"555-0100"}}]""".fromJson[List[Contact]]
// res8: Either[String, List[Contact]] = Right(
//   value = List(Phone(number = "555-0100"))
// )
```

:::tip[Scala 3]
On Scala 3 the same alternatives can be an `enum` with `derives JsonCodec`:

```scala
enum Contact derives JsonCodec {
  case Email(address: String)
  case Phone(number: String)
}
```

An enum case is written the same way, so `Contact.Phone("555-0100")` encodes as `{"Phone":{"number":"555-0100"}}`.
:::

## 6. Put It Together with Optional and List Fields

Now combine everything into a `Profile`. It holds the `Person` from step 4, the `Contact` list from step 5, a nickname that may be missing, and a list of tags. `Option` and `List` already have codecs, so the derived codec needs nothing extra:

```scala
final case class Profile(
  person: Person,
  nickname: Option[String],
  tags: List[String],
  contacts: List[Contact]
)

object Profile {
  implicit val codec: JsonCodec[Profile] = DeriveJsonCodec.gen[Profile]
}
```

A `None` is left out of the JSON, and a `List` becomes a JSON array:

```scala
val profile = Profile(alice, None, List("admin", "dev"), contacts)
// profile: Profile = Profile(
//   person = Person(
//     name = "Alice",
//     age = 30,
//     address = Address(street = "1 Main St", city = "Springfield")
//   ),
//   nickname = None,
//   tags = List("admin", "dev"),
//   contacts = List(
//     Email(address = "alice@example.com"),
//     Phone(number = "555-0100")
//   )
// )

println(profile.toJsonPretty)
// {
//   "person" : {
//     "name" : "Alice",
//     "age" : 30,
//     "address" : {
//       "street" : "1 Main St",
//       "city" : "Springfield"
//     }
//   },
//   "tags" : [
//     "admin",
//     "dev"
//   ],
//   "contacts" : [
//     {
//       "Email" : {
//         "address" : "alice@example.com"
//       }
//     },
//     {
//       "Phone" : {
//         "number" : "555-0100"
//       }
//     }
//   ]
// }
```

A missing `nickname` field decodes as `None`, so the encoded text decodes back to the same value:

```scala
profile.toJson.fromJson[Profile]
// res11: Either[String, Profile] = Right(
//   value = Profile(
//     person = Person(
//       name = "Alice",
//       age = 30,
//       address = Address(street = "1 Main St", city = "Springfield")
//     ),
//     nickname = None,
//     tags = List("admin", "dev"),
//     contacts = List(
//       Email(address = "alice@example.com"),
//       Phone(number = "555-0100")
//     )
//   )
// )
```

## 7. Handle a Decode Error

Bad input never throws. It comes back as a `Left` holding a message. The message is a path in `jq` style, followed by a note in parentheses that says what went wrong.

A value of the wrong type:

```scala
"""{"street":"1 Main St","city":42}""".fromJson[Address]
// res12: Either[String, Address] = Left(value = ".city(expected string)")
```

A missing field:

```scala
"""{"street":"1 Main St"}""".fromJson[Address]
// res13: Either[String, Address] = Left(value = ".city(missing)")
```

Text that is not JSON at all:

```scala
"""{"street": womp}""".fromJson[Address]
// res14: Either[String, Address] = Left(value = ".street(expected string)")
```

In the full `Profile`, a failure deep inside the model keeps the whole path, so you can see exactly where the problem sits:

```scala
"""{"person":{"name":"Alice","age":30,"address":{"street":"1 Main St","city":false}},"tags":[],"contacts":[]}""".fromJson[Profile]
// res15: Either[String, Profile] = Left(
//   value = ".person.address.city(expected string)"
// )
```

Because the result is an ordinary `Either`, you handle it with `fold`, `map`, or a `match`, as with any other `Either`:

```scala
"""{"street":"1 Main St"}""".fromJson[Address] match {
  case Right(address) => s"Got ${address.city}"
  case Left(error)    => s"Could not decode: $error"
}
// res16: String = "Could not decode: .city(missing)"
```

## 8. Where Next

You can now encode, decode, nest, model alternatives, and read errors. To practise on a larger example, continue with the next tutorial, [Model a Real API Payload](tutorial/model-an-api-payload.md). Or pick the guide that matches your next task:

- [Control the JSON a type encodes to](how-to/encoding.md) when the default JSON shape is not the one you need.
- [Decode JSON whose shape differs from your case class](how-to/decoding.md) when the JSON keys or nesting do not match your fields.
- [Rename fields](how-to/field-names.md) when the JSON names differ from your Scala names.
- [Change how sealed traits look in JSON](how-to/sealed-traits.md), for example with a discriminator field instead of the wrapper key.
- [Optional, default and null fields](how-to/optional-and-default-fields.md) for missing, `null` and empty fields.
- [Process large JSON as a stream](how-to/streaming.md) on the JVM.

For every option and method, see the [Reference](reference/index.md). For why ZIO JSON works the way it does, see [How ZIO JSON Works](explanation/how-it-works.md).
