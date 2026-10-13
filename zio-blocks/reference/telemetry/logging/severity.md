# Severity

> The 24-level log severity scale following the OpenTelemetry log data model: Trace, Debug, Info, Warn, Error, Fatal, four levels each.

`Severity` is the level attached to every [`LogRecord`](./log-record.md) — it says how important the message is and drives which records a severity filter keeps. It follows the OpenTelemetry log data model: 24 numeric levels grouped into six named categories with four gradations each, fine enough for detailed instrumentation yet compatible with the familiar five-level `TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR` scale. Every level shares its category's `text` (all four `Trace` levels report `"TRACE"`), so backends that expect the coarse names still work.

```scala
sealed trait Severity {
  def number: Int    // 1 to 24
  def text:   String // the category name: "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"
}

object Severity {
  case object Trace extends Severity  // number = 1; then Trace2, Trace3, Trace4 (2-4)
  case object Debug extends Severity  // number = 5; then Debug2, Debug3, Debug4 (6-8)
  case object Info  extends Severity  // number = 9; then Info2, Info3, Info4 (10-12)
  case object Warn  extends Severity  // number = 13; then Warn2, Warn3, Warn4 (14-16)
  case object Error extends Severity  // number = 17; then Error2, Error3, Error4 (18-20)
  case object Fatal extends Severity  // number = 21; then Fatal2, Fatal3, Fatal4 (22-24)

  def fromNumber(n: Int): Option[Severity]      // Some when 1 <= n <= 24
  def fromText(text: String): Option[Severity]  // case-insensitive; the six category names
}
```

The six category objects (`Trace`, `Debug`, `Info`, `Warn`, `Error`, `Fatal`) are what you name day to day; the numbered gradations exist for instrumentation that needs finer distinctions.

## Filtering by Severity

The main use of `Severity` is to set a floor below which records are dropped before any `LogRecord` is built. Set a global floor with `log.setMinSeverity`, or a per-namespace override that takes precedence for matching call sites:

```scala
import zio.blocks.telemetry._

log.setMinSeverity(Severity.Warn)              // globally drop Trace, Debug, Info
log.setMinSeverity("com.example.db", Severity.Debug) // but keep Debug for the query layer

log.debug("suppressed by the global floor")
log.warn("emitted")
```

`fromText` and `fromNumber` parse a level from an external source — an environment variable or an OTLP protobuf field — returning `None` for anything out of range:

```scala
import zio.blocks.telemetry._

val level = Severity.fromText("ERROR").getOrElse(Severity.Info) // Severity.Error, number 17
log.setMinSeverity(level)
```

## See Also

- [LogRecord](./log-record.md) — the record whose level `Severity` sets.
- [Logging](./index.md) — the `log.*` emit methods and the full filtering API.
