# Type Mappings

> ZIO Bson provides `BsonEncoder` and `BsonDecoder` typeclass instances for a wide range of Scala and Java types.
> The encoder determines the primary BSON type a value is written as.
> The decoder accepts the primary type and, where it makes sense, additional fallback BSON types.

## Scala / Java to BSON Type Mappings

| Scala / Java Type | Encoder → BSON | Decoder fallbacks |
|---|---|---|
| `Boolean` | `BOOLEAN` | — |
| `Byte` | `INT32` | `INT64` (range check), `DOUBLE` (lossless), `DECIMAL128` (lossless), `STRING` |
| `Short` | `INT32` | `INT64` (range check), `DOUBLE` (lossless), `DECIMAL128` (lossless), `STRING` |
| `Int` | `INT32` | `INT64` (range check), `DOUBLE` (lossless), `DECIMAL128` (lossless), `STRING` |
| `Long` | `INT64` | `INT32`, `DOUBLE` (lossless), `DECIMAL128` (lossless), `STRING` |
| `Float` | `DOUBLE` | `INT32`, `INT64`, `DECIMAL128` (lossless), `STRING` |
| `Double` | `DOUBLE` | `INT32`, `INT64`, `DECIMAL128` (lossless), `STRING` |
| `java.math.BigDecimal` | `DECIMAL128` | `INT32`, `INT64`, `DOUBLE`, `STRING` |
| `BigDecimal` | `DECIMAL128` | `INT32`, `INT64`, `DOUBLE`, `STRING` |
| `java.math.BigInteger` | `STRING` | `INT32`, `INT64`, `DOUBLE` (lossless), `DECIMAL128` (lossless) |
| `BigInt` | `STRING` | `INT32`, `INT64`, `DOUBLE` (lossless), `DECIMAL128` (lossless) |
| `String` | `STRING` | `SYMBOL` |
| `Char` | `STRING` | `SYMBOL` |
| `scala.Symbol` | `STRING` | `SYMBOL` |
| `UUID` | `STRING` | `SYMBOL` |
| `java.util.Currency` | `STRING` | `SYMBOL` |
| `DayOfWeek` | `STRING` | `SYMBOL` |
| `Month` | `STRING` | `SYMBOL` |
| `MonthDay` | `STRING` | `SYMBOL` |
| `Year` | `INT32` | `INT64` (range check), `DOUBLE` (lossless), `DECIMAL128` (lossless), `STRING` |
| `YearMonth` | `STRING` | `SYMBOL` |
| `ZoneId` | `STRING` | `SYMBOL` |
| `ZoneOffset` | `STRING` | `SYMBOL` |
| `Duration` | `STRING` | `SYMBOL` |
| `Period` | `STRING` | `SYMBOL` |
| `Instant` | `DATE_TIME` | — |
| `LocalDate` | `DATE_TIME` | — |
| `LocalDateTime` | `DATE_TIME` | — |
| `LocalTime` | `DATE_TIME` | — |
| `OffsetDateTime` | `DOCUMENT` | — |
| `OffsetTime` | `DOCUMENT` | — |
| `ZonedDateTime` | `DOCUMENT` | — |
| `org.bson.types.ObjectId` | `OBJECT_ID` | — |
| `Option[A]` | same as `A` / `NULL` | — |
| `Array[Byte]` | `BINARY` | — |
| `Iterable[Byte]` subtype | `BINARY` | — |
| `NonEmptyChunk[Byte]` | `BINARY` | — |
| `Array[A]` (non-byte) | `ARRAY` | — |
| `Iterable[A]` subtype (non-byte) | `ARRAY` | — |
| `NonEmptyChunk[A]` (non-byte) | `ARRAY` | — |
| `Map[K, V]` | `DOCUMENT` | — |
| `T <: BsonValue` | any (pass-through) | — |

## Special Cases

- **`Byte` and `Short`** are decoded via the `Int` decoder followed by a range check. All BSON numeric types and `STRING` are accepted, but the decoded value must fit within the type's range, otherwise a decoder error is returned.
- **`BigInt` and `java.math.BigInteger`** encode to `STRING` but decode from all numeric BSON types. Conversion from `DOUBLE` and `DECIMAL128` requires the value to be a lossless integer (no fractional part).
- **`String`** decoder also accepts `SYMBOL` for backward compatibility with legacy BSON documents. Every type that decodes through the `String` decoder (`Char`, `Symbol`, `UUID`, `Currency`, `DayOfWeek`, `Month`, `MonthDay`, `YearMonth`, `ZoneId`, `ZoneOffset`, `Duration`, `Period`) accepts `SYMBOL` as well. `DayOfWeek` and `Month` decode case-insensitively.
- **`Instant`, `LocalDate`, `LocalDateTime` and `LocalTime`** are stored as `DATE_TIME`, which has millisecond precision. Sub-millisecond precision is lost when encoding, and the date and time are interpreted in UTC.
- **`Char`** decoder requires the string to be exactly one UTF-16 code unit. A character outside the Basic Multilingual Plane, such as an emoji, is stored as two code units and is rejected.
- **`OffsetDateTime`** and **`OffsetTime`** encode as a `DOCUMENT` with two fields: `date_time` (`DATE_TIME`) and `offset` (`STRING`). The decoder rejects any other field in the document.
- **`ZonedDateTime`** encodes as a `DOCUMENT` with two fields: `date_time` (`DATE_TIME`) and `zone_id` (`STRING`). The decoder rejects any other field in the document.
- **`Option[A]`** encodes `None` as `NULL` and `Some(a)` using the encoder for `A`. The decoder returns `None` for `NULL` and `Some(a)` for any value accepted by the decoder for `A`. When a field is missing from a document, the decoder also returns `None`.
- **`Map[K, V]`** needs a field codec for `K`. Only `String`, `Int` and `Long` keys have one built in. Other key types need a custom `BsonFieldEncoder` and `BsonFieldDecoder`, as described in [Field Codecs](./bson-field-codecs.md). A `None` value in a map is written as `NULL`, not skipped.

## BSON Types Without Scala Mappings

The following BSON types have no dedicated Scala or Java codec in this library. They are accessible only via the `T <: BsonValue` pass-through encoder/decoder using the underlying MongoDB Java driver:

| BSON Type | Notes |
|---|---|
| `SYMBOL` | Deprecated BSON type; accepted by the `String` decoder for backward compatibility, but no encoder exists |
| `UNDEFINED` | Deprecated BSON type |
| `REGULAR_EXPRESSION` | — |
| `DB_POINTER` | Deprecated BSON type |
| `JAVASCRIPT` | — |
| `JAVASCRIPT_WITH_SCOPE` | — |
| `TIMESTAMP` | MongoDB internal replication timestamp |
| `MIN_KEY` | — |
| `MAX_KEY` | — |
| `END_OF_DOCUMENT` | Internal sentinel used by the reader loop; never a value |
