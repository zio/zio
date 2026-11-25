# Validate Data While Decoding

> Reject bad values during decoding with mapOrFail and transformOrFail, read the error paths, and use refined types or strict objects.

Goal: make decoding fail when a value is well-formed JSON but is not acceptable to your program, for example an age of zero or less, or an email with no `@`.

The tool is `JsonDecoder#mapOrFail`. It runs a function on a decoded value. The function returns `Right` to accept or `Left` with a message to reject. The failure is part of decoding, so the bad value never reaches your code. Every example below uses this import:

```scala
import zio.json._
```

## 1. Validate one value type

Wrap the raw type in a small case class, then build its decoder from an existing decoder with `mapOrFail`:

```scala
final case class Email(value: String)

object Email {
  implicit val decoder: JsonDecoder[Email] =
    JsonDecoder[String].mapOrFail { s =>
      if (s.contains("@")) Right(Email(s))
      else Left("email must contain @")
    }

  implicit val encoder: JsonEncoder[Email] =
    JsonEncoder[String].contramap(_.value)
}
```

`JsonEncoder#contramap` is the encoder side. It turns an `Email` back into a `String` so the JSON stays a plain string.

The same pattern validates `Map` keys: build a `JsonFieldDecoder` with `JsonFieldDecoder[String].mapOrFail`. See [Use Your Own Type as a Map Key](custom-map-keys.md).

## 2. Validate both directions with transformOrFail

When you want encoder and decoder together, use a `JsonCodec` and `transformOrFail`. Its first function can fail. Its second function cannot:

```scala
final case class Age(value: Int)

object Age {
  implicit val codec: JsonCodec[Age] =
    JsonCodec[Int].transformOrFail(
      n => if (n > 0) Right(Age(n)) else Left("age must be positive"),
      _.value
    )
}
```

## 3. Use the types in a case class

Fields of these types are checked as the case class is decoded:

```scala
final case class Signup(name: String, age: Age, email: Email)

object Signup {
  implicit val decoder: JsonDecoder[Signup] = DeriveJsonDecoder.gen[Signup]
}
```

Valid input decodes as normal:

```scala
"""{"name":"Ada","age":36,"email":"ada@example.com"}""".fromJson[Signup]
// res1: Either[String, Signup] = Right(
//   value = Signup(
//     name = "Ada",
//     age = Age(value = 36),
//     email = Email(value = "ada@example.com")
//   )
// )
```

## 4. Read the error

A rejected value produces a `Left`. The string starts with the path to the field, written as `.fieldName`, then your message in parentheses:

```scala
"""{"name":"Ada","age":-1,"email":"ada@example.com"}""".fromJson[Signup]
// res2: Either[String, Signup] = Left(value = ".age(age must be positive)")

"""{"name":"Ada","age":36,"email":"ada.example.com"}""".fromJson[Signup]
// res3: Either[String, Signup] = Left(value = ".email(email must contain @)")
```

When the value is not nested in a field, such as decoding an `Age` alone, the path is empty and only the parenthesised message remains:

```scala
"0".fromJson[Age]
// res4: Either[String, Age] = Left(value = "(age must be positive)")
```

## 5. Know that the first failure wins

Decoding stops at the first bad value. It does not collect a list of errors. Fields are read in the order they appear in the JSON text, so when two fields are bad, only the first one in the text is reported:

```scala
"""{"name":"Ada","age":-1,"email":"nope"}""".fromJson[Signup]
// res5: Either[String, Signup] = Left(value = ".age(age must be positive)")

"""{"name":"Ada","email":"nope","age":-1}""".fromJson[Signup]
// res6: Either[String, Signup] = Left(value = ".email(email must contain @)")
```

If you need every error at once, decode to the [`Json` AST](../reference/json-ast.md) or to plain types and run your own validation after decoding.

## 6. Check a rule across fields

`mapOrFail` also works on a whole object. Use it for a rule that involves more than one field:

```scala
final case class Window(from: Int, to: Int)

object Window {
  implicit val decoder: JsonDecoder[Window] =
    DeriveJsonDecoder.gen[Window].mapOrFail { r =>
      if (r.from <= r.to) Right(r) else Left("from must not be after to")
    }
}
```

An object whose fields are each valid but break the rule fails as a whole:

```scala
"""{"from":5,"to":1}""".fromJson[Window]
// res7: Either[String, Window] = Left(value = "(from must not be after to)")
```

Because the check runs after the object is built, the error has no path below the object: here it is empty, and inside another case class it would be the path of the field that holds the `Window`.

## 7. Use refined types

The `zio-json-interop-refined` module gives you encoders and decoders for [refined](https://github.com/fthomas/refined) types, so a predicate such as `NonEmpty` or `Positive` replaces the hand-written check. Add it to your build:

```sbt
libraryDependencies += "dev.zio" %% "zio-json-interop-refined" % "1.1.3"
```

With the module on the classpath, import its instances and use a refined type as a field type:

```scala
import zio.json._
import zio.json.interop.refined._

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty

final case class Account(owner: String Refined NonEmpty)

object Account {
  implicit val decoder: JsonDecoder[Account] = DeriveJsonDecoder.gen[Account]
}
```

A failed predicate shows up as a `Left` such as `.owner(Predicate isEmpty() did not fail.)`. See [Refined interop](../interop/refined.md) for the details.

## 8. Reject unknown fields

Validation can also mean refusing JSON that has extra keys. Put `@jsonNoExtraFields` on the case class, and any field not in the class makes decoding fail:

```scala
@jsonNoExtraFields
final case class Login(user: String)

object Login {
  implicit val decoder: JsonDecoder[Login] = DeriveJsonDecoder.gen[Login]
}
```

An unknown `admin` key is now an error:

```scala
"""{"user":"ada","admin":true}""".fromJson[Login]
// res9: Either[String, Login] = Left(value = "(invalid extra field)")
```

Without the annotation, extra fields are skipped. See [Optional, default and null fields](./optional-and-default-fields.md) for how missing fields are handled, and the [Annotations reference](../reference/annotations.md) for every annotation.
