# Standard Type Reference

> ZIO Schema provides a number of built-in primitive types, that we can use to represent our data. Each one has a `defaultValue` that generates a value of the type. The following table lists every standard type and whether `defaultValue` succeeds on each platform. A ❌ means that `defaultValue` returns a `Left` on that platform, because the value cannot be generated there:

|Standard Type|JVM|ScalaJS|Scala Native|
|--------------|:--------------:|:--------------:|:--------------:|
|`Boolean`|✅|✅|✅|
|`Byte`|✅|✅|✅|
|`Char`|✅|✅|✅|
|`Chunk[Byte]`|✅|✅|✅|
|`Double`|✅|✅|✅|
|`Float`|✅|✅|✅|
|`Int`|✅|✅|✅|
|`Long`|✅|✅|✅|
|`Short`|✅|✅|✅|
|`String`|✅|✅|✅|
|`Unit`|✅|✅|✅|
|`java.math.BigDecimal`|✅|✅|✅|
|`java.math.BigInteger`|✅|✅|✅|
|`java.time.DayOfWeek`|✅|✅|✅|
|`java.time.Duration`|✅|✅|✅|
|`java.time.Instant`|✅|✅|✅|
|`java.time.LocalDate`|✅|✅|✅|
|`java.time.LocalDateTime`|✅|✅|✅|
|`java.time.LocalTime`|✅|✅|✅|
|`java.time.Month`|✅|✅|✅|
|`java.time.MonthDay`|✅|✅|✅|
|`java.time.OffsetDateTime`|✅|✅|✅|
|`java.time.OffsetTime`|✅|✅|✅|
|`java.time.Period`|✅|✅|✅|
|`java.time.Year`|✅|✅|✅|
|`java.time.YearMonth`|✅|✅|✅|
|`java.time.ZoneId`|✅|✅|✅|
|`java.time.ZoneOffset`|✅|✅|✅|
|`java.time.ZonedDateTime`|✅|✅|✅|
|`java.util.Currency`|✅|❌|❌|
|`java.util.UUID`|✅|❌|✅|
