# Optional

> `Optional[A]` describes a value that may be absent. It plays the same role as the standard `Option` type, and it has the same two cases: a value that is present and a value that is absent.

The difference is that `Optional` defines implicit conversions from a plain value `A` and from an `Option[A]` to `Optional[A]`. `Optional` is designed for wrapping external data models in which most fields are optional, so that callers can write the field values directly. It is also a data type from ZIO Prelude, so it has instances for some of the functional abstractions in the library, and code written against those abstractions handles an optional value without special cases.

## Motivation

Generated or externally defined models, such as the classes produced from a schema, tend to have many optional fields. With `Option` every call site has to write `Some(...)` for each field that is set and `None` for each that is not, which hides the fields that matter in noise. With `Optional` as the field type, a caller passes a plain value for a field that is set, an `Option` it already has, or `Optional.Absent`, and the conversion happens implicitly. Use `Optional` for that kind of model, and prefer `Option` everywhere else, since the implicit conversions can also hide mistakes in code that is not shaped like this.

We import `Optional` from the `zio.prelude.data` package:

```scala
import zio.prelude.data.Optional
```

## Present and Absent Values

An `Optional` is either `Optional.Present`, which holds a value, or `Optional.Absent`, which holds nothing:

```scala
val present: Optional[Int] =
  Optional.Present(42)
// present: Optional[Int] = Present(get = 42)

val absent: Optional[Int] =
  Optional.Absent
// absent: Optional[Int] = Absent
```

Scala converts values to `Optional` implicitly, so a plain value works where an `Optional` is required. A value becomes `Present`, and an `Option` becomes `Present` when it holds a value and `Absent` when it is `None`:

```scala
val fromValue: Optional[String] =
  "hello"
// fromValue: Optional[String] = Present(get = "hello")

val fromOption: Optional[Int] =
  Option.empty[Int]
// fromOption: Optional[Int] = Absent
```

## Working With an Optional Value

The operators on `Optional` mirror the operators on `Option`. `map` and `flatMap` transform a present value and leave an absent value unchanged:

```scala
present.map(_ + 1)
// res1: Optional[Int] = Present(get = 43)

absent.map(_ + 1)
// res2: Optional[Int] = Absent
```

`getOrElse` returns the value if it is present and the default otherwise. `fold` takes one function for the absent case and one for the present case:

```scala
absent.getOrElse(0)
// res3: Int = 0

present.fold("nothing")(value => s"found $value")
// res4: String = "found 42"
```

Converting to other types uses the names the standard library uses. `toOption` returns a standard `Option`, and `toLeft` and `toRight` convert to an `Either`:

```scala
present.toOption
// res5: Option[Int] = Some(value = 42)

present.toRight("missing")
// res6: Either[String, Int] = Right(value = 42)
```

`filter`, `exists`, `forall`, and `contains` check a present value against a condition. An absent value fails each of these checks:

```scala
present.filter(_ > 40)
// res7: Optional[Int] = Present(get = 42)

absent.exists(_ > 40)
// res8: Boolean = false
```

## Optional Fields in a Model

The implicit conversions make constructing a model with many optional fields concise. A field accepts a plain value, an `Option`, or `Optional.Absent`:

```scala
final case class Profile(nickname: Optional[String], age: Optional[Int], city: Optional[String])

val knownCity: Option[String] = Some("Tehran")
// knownCity: Option[String] = Some(value = "Tehran")

Profile("zed", Optional.Absent, knownCity)
// res9: Profile = Profile(
//   nickname = Present(get = "zed"),
//   age = Absent,
//   city = Present(get = "Tehran")
// )
```

## Instances

`Optional` has a `ForEach` instance, so it can be traversed with any operator that works on collections, and `IdentityBoth`, `IdentityEither`, `IdentityFlatten` and `CommutativeBoth` instances, so it works with the generic combining operators. It also has an `Equal` instance when its element type has one:

```scala
import zio.prelude._

Optional.Present(1) === Optional.Present(1)
// res10: Boolean = true

Optional.Present(1) === Optional.Absent
// res11: Boolean = false

IdentityBoth[Optional].both(Optional.Present(1), Optional.Present("a"))
// res12: Optional[(Int, String)] = Present(get = (1, "a"))
```

`Optional` extends the standard collection interface for single-element collections, so `toList`, `toVector` and `toChunk` convert it to a collection that has zero or one element, and `iterator` and `foreach` work as they do on `Option`.

## Related Abstractions

`Option` is the standard type to use when the implicit conversions are not needed. [NonEmptyList](./nonemptylist.md) and the other non-empty types are the opposite case, where absence is ruled out by the type. [ForEach](../functional-abstractions/parameterized-types/foreach.md) and [IdentityBoth](../functional-abstractions/parameterized-types/identityboth.md) are the abstractions that `Optional` provides instances for.
