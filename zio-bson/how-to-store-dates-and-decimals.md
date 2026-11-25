# Store Dates, Times and Decimal Numbers

> MongoDB has a date type with millisecond precision and a decimal type with a fixed number of digits. Java's time classes and Scala's big numbers carry more information than that. This guide shows what each type becomes in BSON, so we can choose the right Scala type for a field.

## Problem

## Know What Each Date Type Stores

`Instant`, `LocalDate`, `LocalDateTime` and `LocalTime` are written as a BSON `DATE_TIME`, which counts milliseconds, and the value is interpreted in UTC. Any precision below a millisecond is lost when encoding:

```scala
import zio.bson._
import java.time.Instant

val precise = Instant.parse("2026-01-01T10:00:00.123456789Z")
// precise: Instant = 2026-01-01T10:00:00.123456789Z

val stored = precise.toBsonValue
// stored: org.bson.BsonValue = BsonDateTime{value=1767261600123}

stored.as[Instant]
// res0: Either[String, Instant] = Right(value = 2026-01-01T10:00:00.123Z)
```

If the sub-millisecond part matters, store the value as an ISO string instead, by wrapping a `String` codec as shown in [Write a Codec by Hand](./how-to-write-a-codec-by-hand.md).

## Keep the Zone with OffsetDateTime and ZonedDateTime

A `DATE_TIME` has no zone. When we need the offset or the zone id back, we use `OffsetDateTime` or `ZonedDateTime`. They are written as a document with the date and the offset or zone, as listed in [Type Mappings](./types.md):

```scala
import java.time.{OffsetDateTime, ZoneOffset}

val local = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(3))
// local: OffsetDateTime = 2026-01-01T10:00+03:00

val bson = local.toBsonValue
// bson: org.bson.BsonValue = {"date_time": {"$date": "2026-01-01T07:00:00Z"}, "offset": "+03:00"}

println(bson)
// {"date_time": {"$date": "2026-01-01T07:00:00Z"}, "offset": "+03:00"}
bson.as[OffsetDateTime]
// res2: Either[String, OffsetDateTime] = Right(value = 2026-01-01T10:00+03:00)
```

## Choose Between Decimal and String for Big Numbers

`BigDecimal` and `java.math.BigDecimal` are written as `DECIMAL128`, which keeps up to 34 significant digits and supports range queries and arithmetic in the database. `BigInt` and `java.math.BigInteger` are written as `STRING`, so any size fits, but the database cannot compare them as numbers:

```scala
println(BigDecimal("12.50").toBsonValue)
// BsonDecimal128{value=12.50}
println(BigInt("123456789012345678901234567890").toBsonValue)
// BsonString{value='123456789012345678901234567890'}
```

Use `BigDecimal` for money and measurements that the database must filter or sort, and `BigInt` for large identifiers that are only stored and loaded.

## Related Pages

- [Type Mappings](./types.md) lists the BSON type and the decoder fallbacks of every supported type.
- [BsonBuilder](./bson-builder.md) has `BsonBuilder.date` for building a date value by hand.
