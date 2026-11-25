# Getting The Default Value

> Some tools need a sample value for a type, for example to fill a form, to build a test fixture, or to show a placeholder. The `defaultValue` method of a schema returns such a value. It has this signature:

```scala
sealed trait Schema[A] {
  def defaultValue: scala.util.Either[String, A]
}
```

The method returns `Right` with a value when the schema can produce one, and `Left` with an error message when it cannot.

## Standard Types

Every standard type has a default value. For example, `String` defaults to the empty string, and `Int` defaults to `0`:

```scala
import zio.schema._

Schema[String].defaultValue
// res0: Either[String, String] = Right(value = "")
Schema[Int].defaultValue
// res1: Either[String, Int] = Right(value = 0)
```

Some standard defaults are not constant. `UUID` produces a new random value on each call. `LocalDate`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`, `Year`, and `YearMonth` use the current clock. `ZoneId` uses the system time zone, `DayOfWeek` uses the first day of the week for the default locale, and `Currency` uses the currency of the default locale, so it returns `Left` when that locale has no country. The platform support for each type is listed in the [Standard Type Reference](../standard-type-reference.md).

## Default Values by Schema Kind

Each kind of schema computes its default in its own way. The following table lists the rule for each kind:

| Schema kind                              | `defaultValue`                                                                  |
| ---------------------------------------- | ------------------------------------------------------------------------------- |
| `Primitive`                              | The value set with `Schema#default`, otherwise the default of the standard type |
| `Record` (case classes, `GenericRecord`) | Built from the default of each field; `Left` if any field has none              |
| `Enum` (sealed traits)                   | The default of the first case                                                   |
| `Optional`                               | `Right(None)`                                                                   |
| `Sequence`, `Set`, `Map`                 | An empty collection                                                             |
| `NonEmptySequence`, `NonEmptyMap`        | `Left`, because an empty value is not allowed                                   |
| `Tuple2`                                 | A pair of the two defaults; `Left` if either side has none                      |
| `Either`                                 | `Left` of the left default, otherwise `Right` of the right default              |
| `Fallback`                               | `Both` when both sides have a default, otherwise the side that has one          |
| `Transform`                              | The default of the underlying schema, passed through the forward function       |
| `Lazy`                                   | The default of the wrapped schema                                               |
| `Fail`                                   | `Left` with the failure message                                                 |
| `Dynamic`                                | `Right(DynamicValue.NoneValue)`                                                 |

A `Transform` is how schemas such as `Schema[BigDecimal]` and `Schema[java.net.URI]` are built, so their default is the default of the underlying primitive mapped to the new type.

## Overriding a Primitive Default with `default`

The `Schema#default` method returns a copy of a primitive schema that uses the value you pass as its default:

```scala
val retries: Schema[Int] = Schema[Int].default(3)
// retries: Schema[Int] = Primitive(
//   standardType = int,
//   annotations = IndexedSeq(fieldDefaultValue(value = 3))
// )

retries.defaultValue
// res2: Either[String, Int] = Right(value = 3)
```

The original `Schema[Int]` keeps its own default, so the override affects only the new schema.

:::caution
`Schema#default` stores the value as a `fieldDefaultValue` annotation on the schema, and only `Primitive` schemas (and a `Lazy` that wraps one) read it. Every other kind of schema ignores it: records, enumerations, `Transform` schemas, optionals, and collections keep computing their default with the rules above. For example, `Schema[BigDecimal]` is a `Transform`, so the override has no effect:

```scala
Schema[BigDecimal].default(BigDecimal(5)).defaultValue
// res3: Either[String, BigDecimal] = Right(value = 0)
```

To give a case class field a default, use a Scala default parameter or the `fieldDefaultValue` annotation on the field, as described in the next section.
:::

## Records and Enumerations

A record builds its default from the default value of each field. For each field, it first looks for a `fieldDefaultValue` annotation on the field, and otherwise it uses the default of the field's schema. If any field has no default, the record has none. The following case class uses only standard types, so every field has one:

```scala
case class Settings(host: String, port: Int)

object Settings {
  implicit val schema: Schema[Settings] = DeriveSchema.gen[Settings]
}
```

The default record holds the default of each field's type:

```scala
Settings.schema.defaultValue
// res4: Either[String, Settings] = Right(
//   value = Settings(host = "", port = 0)
// )
```

When a case class declares Scala default parameters, `DeriveSchema.gen` turns each one into a `fieldDefaultValue` annotation on its field. The record default then uses those values instead of the defaults of the field types:

```scala
case class Server(host: String = "localhost", port: Int = 8080)

object Server {
  implicit val schema: Schema[Server] = DeriveSchema.gen[Server]
}
```

The default `Server` uses the declared parameter values:

```scala
Server.schema.defaultValue
// res5: Either[String, Server] = Right(
//   value = Server(host = "localhost", port = 8080)
// )
```

The same `fieldDefaultValue` annotation also makes the field optional when decoding, so codecs such as the JSON codec fill in the declared value when the field is missing. The [Schema Annotations](../schema-annotations.md) page shows the annotation applied by hand.

An enumeration uses the default value of its first case, in the order the cases appear in the schema.
