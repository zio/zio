# HeaderCodec and QueryCodec

> `HeaderCodec[A]` and `QueryCodec[A]` encode a whole value of type `A` into `Headers` or `QueryParams` and decode it back. Both are derived from `Schema[A]` through a format — `DefaultHeaderFormat` or `DefaultQueryFormat` — which pairs a MIME type with the `Deriver` that builds instances. The types involved:

```scala
abstract class HeaderCodec[A] extends Codec[Headers, HeadersBuilder, A] {
  def encodeToHeaders(value: A): Headers
}

abstract class QueryCodec[A] extends Codec[QueryParams, QueryParamsBuilder, A] {
  def encodeToQueryParams(value: A): QueryParams
}

abstract class HeaderFormat[TC[A] <: HeaderCodec[A]](val mimeType: String, val deriver: Deriver[TC])
abstract class QueryFormat[TC[A] <: QueryCodec[A]](val mimeType: String, val deriver: Deriver[TC])
```

## Motivation

The extension classes on [the schema page](./schema.md) read one field at a time: `request.query[Int]("page")` pulls a single parameter and gives you an `Either`. That is the right shape when a handler wants two or three values out of a request.

It is the wrong shape when the request *is* a value. A search endpoint taking eight query parameters becomes eight extractions, eight error branches, and a constructor call that has to be kept in step with all of them. Adding a parameter means touching four places.

A codec collapses that to one call. Define the case class, derive a `QueryCodec` from its schema, and `QueryCodec#decode` gives you `Either[SchemaError, SearchRequest]` for the whole thing. Adding a field to the case class adds a parameter, with no other change — the same bargain `Schema` makes everywhere else in the library.

The two directions are symmetric, which matters for clients as much as servers: the same codec that decodes an incoming request encodes an outgoing one.

## Quick Showcase

Derive a codec from a schema, then round-trip a value through it:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._

final case class Search(page: Int, term: String, active: Boolean)

object Search {
  implicit val schema: Schema[Search] = Schema.derived[Search]
}

val codec = Schema[Search].derive(DefaultQueryFormat)
```

Encoding produces one parameter per field, named after the field:

```scala
codec.encodeToQueryParams(Search(2, "boots", active = true))
// res0: QueryParams = QueryParams((page,2), (term,boots), (active,true))
```

Decoding is the inverse, and reports a `SchemaError` rather than throwing:

```scala
codec.decode(codec.encodeToQueryParams(Search(2, "boots", active = true)))
// res1: Either[SchemaError, Search] = Right(
//   Search(page = 2, term = "boots", active = true)
// )
```

## Deriving a Codec

Both codecs are derived the same way: call `Schema#derive` with the format object. The implicit `Derivable` that each format supplies is what lets a format stand in for its deriver:

```scala
trait Schema[A] {
  def derive[D, TC[_]](d: D)(implicit ev: Derivable[D, TC]): TC[A]
  def deriving[TC[_]](deriver: Deriver[TC]): DerivationBuilder[TC, A]
}
```

`DefaultQueryFormat` produces a `QueryCodec`, and `DefaultHeaderFormat` produces a `HeaderCodec`:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._

final case class Trace(traceId: String, apiKey: String)

object Trace {
  implicit val schema: Schema[Trace] = Schema.derived[Trace]
}

val headerCodec = Schema[Trace].derive(DefaultHeaderFormat)
```

The derived codec carries the format's MIME type, which is what an endpoint description uses to advertise the wire shape:

```scala
DefaultHeaderFormat.mimeType
// res3: String = "application/http-headers"
DefaultQueryFormat.mimeType
// res4: String = "application/x-www-form-urlencoded"
```

## Encoding and Decoding

Each codec has a convenience encoder that returns the finished collection, plus the `Codec#encode` and `Codec#decode` methods it inherits.

### `HeaderCodec#encodeToHeaders` and `QueryCodec#encodeToQueryParams`

These build and return the collection directly, which is what application code normally wants:

```scala
abstract class HeaderCodec[A] {
  def encodeToHeaders(value: A): Headers
}
```

Encoding a record yields one entry per field:

```scala
headerCodec.encodeToHeaders(Trace("trace-1", "secret"))
// res5: Headers = Headers(trace-id: trace-1, api-key: secret)
```

:::warning[The builder is a reused thread-local]
`HeaderCodec#encodeToHeaders` and `QueryCodec#encodeToQueryParams` borrow a per-thread builder, reset it, fill it, and snapshot it. The returned collection is safe to keep, but the builder is not reentrant: if a custom codec's `Codec#encode` calls `HeaderCodec#encodeToHeaders` again on the same thread, the inner call resets the buffer the outer one was filling. Inside a custom `Codec#encode`, write to the `output` builder you were handed rather than calling the convenience encoder.
:::

### `Codec#encode` and `Codec#decode`

`Codec#encode` writes into a builder you supply, which is how nested and composed codecs avoid intermediate collections. `Codec#decode` reads a whole collection:

```scala
abstract class QueryCodec[A] {
  def encode(value: A, output: QueryParamsBuilder): Unit
  def decode(input: QueryParams): Either[SchemaError, A]
}
```

A decode failure names the field and what was expected:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._
import zio.http.QueryParams

final case class Search(page: Int, term: String)

object Search {
  implicit val schema: Schema[Search] = Schema.derived[Search]
}

val codec = Schema[Search].derive(DefaultQueryFormat)
```

A missing parameter and a malformed one are both reported as a `SchemaError`, not an exception:

```scala
codec.decode(QueryParams("term" -> "boots"))
// res7: Either[SchemaError, Search] = Left(
//   SchemaError(
//     List(MissingField(source = DynamicOptic(ArraySeq()), fieldName = "page"))
//   )
// )
codec.decode(QueryParams("page" -> "not-a-number", "term" -> "boots"))
// res8: Either[SchemaError, Search] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(ArraySeq()),
//         details = "Malformed query parameter 'page' value 'not-a-number': Cannot parse 'not-a-number' as Int",
//         cause = None
//       )
//     )
//   )
// )
```

## Field Naming

The two codecs disagree about names, and this is the single most important thing to know about them.

| | Field `traceId` becomes | Decoding |
| ------------- | ----------------------- | ---------------- |
| `QueryCodec`  | `traceId` — verbatim     | exact match       |
| `HeaderCodec` | `trace-id` — kebab-case  | case-insensitive  |

`QueryCodec` uses the Scala field name unchanged, because query parameters are conventionally camelCase or snake_case and the schema's name is as good a guess as any.

`HeaderCodec` rewrites camelCase into kebab-case, because that is the conventional shape of an HTTP header name. Two steps are involved: the deriver emits `TRACE-ID`, uppercasing every character and inserting a hyphen before each interior capital, and then `Headers` lowercases every name as it stores it. The second step makes the first unobservable, so what you get back is `trace-id`:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._
import zio.http.Headers

final case class Meta(traceId: String, bigIntValue: BigInt, uuidValue: java.util.UUID)

object Meta {
  implicit val schema: Schema[Meta] = Schema.derived[Meta]
}

val codec = Schema[Meta].derive(DefaultHeaderFormat)
val uuid  = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
```

Every camelCase boundary becomes a hyphen, so `bigIntValue` becomes three segments rather than two:

```scala
codec.encodeToHeaders(Meta("trace-1", BigInt(42), uuid)).toList.map(_._1)
// res10: List[String] = List("trace-id", "big-int-value", "uuid-value")
```

Decoding ignores case, so a client that sends `trace-id` or `TRACE-ID` still decodes:

```scala
codec.decode(Headers("trace-id" -> "t", "big-int-value" -> "42", "UUID-VALUE" -> uuid.toString))
// res11: Either[SchemaError, Meta] = Right(
//   Meta(
//     traceId = "t",
//     bigIntValue = 42,
//     uuidValue = 123e4567-e89b-12d3-a456-426614174000
//   )
// )
```

:::warning[Names are not configurable]
Neither codec offers a name mapper. The transformation is fixed, so a header or parameter whose wire name does not follow the convention cannot be reached by renaming the field — you need a custom codec instance for that type, or the field-at-a-time extraction on [the schema page](./schema.md).
:::

## Supported Field Shapes

Four shapes are supported per field, and they compose one level deep.

### Primitives

Each primitive field is one entry, rendered as its string form:

| Scala type | Example wire value |
| --- | --- |
| `String` | `alice` |
| `Boolean` | `true` |
| `Byte`, `Short`, `Int`, `Long` | `42` |
| `Float`, `Double` | `12.34` |
| `BigInt`, `BigDecimal` | `1234567890123456789` |
| `Char` | `z` — exactly one character |
| `UUID` | `123e4567-e89b-12d3-a456-426614174000` |

A `Char` field with more than one character fails to decode, and the message says so:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._
import zio.http.QueryParams

val charCodec = Schema[Char].derive(DefaultQueryFormat)
```

The error names the expectation rather than the underlying exception:

```scala
charCodec.decode(QueryParams("value" -> "too-long"))
// res13: Either[SchemaError, Char] = Left(
//   SchemaError(
//     List(
//       ConversionFailed(
//         source = DynamicOptic(ArraySeq()),
//         details = "Malformed query parameter 'value' value 'too-long': Expected single character but got 'too-long'",
//         cause = None
//       )
//     )
//   )
// )
```

### Optional Fields

An `Option[A]` field omits its key entirely when `None`, and an absent key decodes back to `None`:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._
import zio.http.QueryParams

final case class Filters(page: Option[Int], term: Option[String])

object Filters {
  implicit val schema: Schema[Filters] = Schema.derived[Filters]
}

val codec = Schema[Filters].derive(DefaultQueryFormat)
```

Encoding drops the absent field rather than emitting an empty value:

```scala
codec.encodeToQueryParams(Filters(Some(7), None))
// res15: QueryParams = QueryParams((page,7))
```

An empty collection decodes to all-`None`, so a request with no parameters is valid rather than an error:

```scala
codec.decode(QueryParams.empty)
// res16: Either[SchemaError, Filters] = Right(
//   Filters(page = None, term = None)
// )
```

### Sequences

A `List`, `Chunk`, or other sequence field becomes **repeated entries under one key**, not a delimited single value:

```scala
import zio.blocks.schema.Schema
import zio.blocks.chunk.Chunk
import zio.http.schema._

final case class Selection(tags: List[String], ids: Chunk[Int])

object Selection {
  implicit val schema: Schema[Selection] = Schema.derived[Selection]
}

val codec = Schema[Selection].derive(DefaultQueryFormat)
```

Two tags and three ids produce five parameters:

```scala
codec.encodeToQueryParams(Selection(List("a", "b"), Chunk(1, 2, 3)))
// res18: QueryParams = QueryParams((tags,a), (tags,b), (ids,1), (ids,2), (ids,3))
```

Decoding collects them back into the declared collection type, preserving order:

```scala
codec.decode(codec.encodeToQueryParams(Selection(List("a", "b"), Chunk(1, 2, 3))))
// res19: Either[SchemaError, Selection] = Right(
//   Selection(tags = List("a", "b"), ids = IndexedSeq(1, 2, 3))
// )
```

Because there is no delimiter, a value containing a comma round-trips correctly — the repeated-key encoding has no escaping problem to solve.

### Wrapper Types

A newtype built with `Schema#transform` encodes as its underlying value, so the wrapper is invisible on the wire:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._

final case class UserId(value: String)

object UserId {
  implicit val schema: Schema[UserId] = Schema[String].transform(UserId(_), _.value)
}

final case class Owned(id: UserId)

object Owned {
  implicit val schema: Schema[Owned] = Schema.derived[Owned]
}

val codec = Schema[Owned].derive(DefaultQueryFormat)
```

The parameter holds the wrapped string, with no trace of the wrapper:

```scala
codec.encodeToQueryParams(Owned(UserId("user-1")))
// res21: QueryParams = QueryParams((id,user-1))
```

If the wrapping function throws — a validating newtype rejecting its input — the failure surfaces as a `SchemaError` from `QueryCodec#decode` rather than as an exception.

## Top-Level Codecs

A schema that is not a record also derives, and the resulting codec uses the single key `value`:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._
import zio.http.{Headers, QueryParams}

val intCodec  = Schema[Int].derive(DefaultQueryFormat)
val listCodec = Schema[List[Int]].derive(DefaultQueryFormat)
```

A top-level primitive is one parameter, and a top-level sequence is repeated parameters, both under `value`:

```scala
intCodec.encodeToQueryParams(42)
// res23: QueryParams = QueryParams((value,42))
listCodec.encodeToQueryParams(List(1, 2))
// res24: QueryParams = QueryParams((value,1), (value,2))
```

`HeaderCodec` applies its naming rule here too, so the single-word name comes back as `value` and is matched case-insensitively on the way in:

```scala
val headerIntCodec = Schema[Int].derive(DefaultHeaderFormat)
// headerIntCodec: HeaderCodec[Int] = zio.http.schema.HeaderCodecDeriver$$anon$3@33407159
headerIntCodec.encodeToHeaders(42).toList
// res25: List[Tuple2[String, String]] = List(("value", "42"))
headerIntCodec.decode(Headers("value" -> "42"))
// res26: Either[SchemaError, Int] = Right(42)
```

## Unsupported Shapes

Three shapes have no encoding, and one of them is a trap.

**Nested records** are rejected during derivation, because a flat namespace of names has no way to express a field whose value is itself a record. Use a flat case class, or extract the nested part separately.

**Top-level `Option`, `Map`, and `DynamicValue`** derive without complaint and then **throw when you encode**:

```scala
import zio.blocks.schema.Schema
import zio.http.schema._

val optionCodec = Schema[Option[Int]].derive(DefaultQueryFormat)
```

Derivation succeeds, so nothing warns you at the point where the mistake was made:

```scala
scala.util.Try(optionCodec.encodeToQueryParams(Some(1))).isFailure
// res28: Boolean = true
```

:::danger[Failure is deferred to the first encode]
`Schema[Option[A]]`, `Schema[Map[K, V]]`, and `Schema[DynamicValue]` all produce a codec that throws on use. Because derivation is where the error belongs and is not where it appears, a codec built at startup can look healthy until the first request that encodes through it. Derive and exercise these codecs in a test rather than trusting that construction succeeded.
:::

A `Map` field inside a record is likewise unsupported. Model the entries you expect as fields, or take the raw collection and read it with the field-at-a-time API.

## Formats

A format is the pairing of a MIME type with a `Deriver`. `HeaderFormat` and `QueryFormat` are the base classes, and each provides an implicit `Derivable` so that `Schema#derive` accepts the format object directly:

```scala
abstract class HeaderFormat[TC[A] <: HeaderCodec[A]](val mimeType: String, val deriver: Deriver[TC]) {
  implicit def derivable: Derivable[this.type, TC]
}
```

The two built-in formats are the only ones needed for the default wire shapes:

| Format | MIME type | Deriver |
| --- | --- | --- |
| `DefaultHeaderFormat` | `application/http-headers` | `HeaderCodecDeriver` |
| `DefaultQueryFormat` | `application/x-www-form-urlencoded` | `QueryCodecDeriver` |

Both are `case object`s extending a sealed abstract class, so they are singletons and can be matched on.

### Defining a Custom Format

Subclass `QueryFormat` or `HeaderFormat` when you want a different MIME type reported for the same derivation strategy, or a different deriver entirely:

```scala
import zio.http.schema._

case object FormUrlEncoded extends QueryFormat[QueryCodec]("application/x-www-form-urlencoded; charset=utf-8", QueryCodecDeriver)
```

The `TC` parameter is the codec type the deriver produces, bounded by `QueryCodec` so that the convenience encoder stays available on whatever it derives.

## Overriding a Single Type

`HeaderCodecDeriver` and `QueryCodecDeriver` are ordinary `Deriver` instances, so the schema module's override mechanism applies. Supply a hand-written codec for one type and let derivation handle the rest:

```scala
import zio.blocks.schema.{Schema, SchemaError}
import zio.blocks.typeid.TypeId
import zio.http.schema._
import zio.http.{QueryParams, QueryParamsBuilder}

val prefixedInt = new QueryCodec[Int] {
  def encode(value: Int, output: QueryParamsBuilder): Unit =
    output.add("value", s"custom-$value")

  def decode(input: QueryParams): Either[SchemaError, Int] =
    input.getFirst("value") match {
      case Some(s) if s.startsWith("custom-") => Right(s.stripPrefix("custom-").toInt)
      case other                              => Left(SchemaError(s"Expected custom- prefix, got: $other"))
    }
}

val codec = Schema[Int].deriving(QueryCodecDeriver).instance(TypeId.int, prefixedInt).derive
```

The override applies wherever that type appears, so the custom rendering is used on both sides:

```scala
codec.encodeToQueryParams(42)
// res31: QueryParams = QueryParams((value,custom-42))
codec.decode(codec.encodeToQueryParams(42))
// res32: Either[SchemaError, Int] = Right(42)
```

Note that `Codec#encode` writes into the supplied builder rather than returning a collection — that is the method to implement, and the convenience encoder is derived from it.

## Choosing Between the Two APIs

Both this page's codecs and [the schema page](./schema.md)'s extension classes read the same collections. They differ in granularity:

| | Codecs | Extension classes |
| --- | --- | --- |
| Unit of work | Whole value | One field |
| Result | `Either[SchemaError, A]` | `Either[QueryParamError, T]` / `Either[HeaderError, T]` |
| Encoding | Yes, symmetric with decoding | Decoding only |
| Names | Fixed by the naming rule | You pass the name |
| Nested records | Unsupported | Not applicable |
| Best for | A request or response that *is* a value | Pulling two or three values out |

Use a codec when the collection maps onto a type you already have. Use the extension classes when it does not, when you need a name the codec cannot produce, or when you want per-field error handling.

## Integration Points

`HeaderCodec` and `QueryCodec` extend `Codec[Out, Builder, A]` from `zio-blocks-schema`, so they participate in the same derivation machinery as every other codec in the library — the `Deriver`, `Derivable`, and instance-override APIs are the schema module's, not this module's.

On the HTTP side they produce and consume `Headers`, `HeadersBuilder`, `QueryParams`, and `QueryParamsBuilder` from `zio-blocks-http-model`; see [the HTTP model](./model.md) for those collections and [Header](./headers.md) for the typed single-header model, which is a different mechanism from the whole-record codecs here.

Errors are `SchemaError` from the schema module rather than this module's `HeaderError` and `QueryParamError`, which belong to the field-at-a-time API on [the schema page](./schema.md).
