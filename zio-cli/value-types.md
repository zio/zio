# Value Types

> Every basic [`Args`](args.md) and [`Options`](options.md) constructor reads one string from the command line and converts it to a Scala value. The conversion is described by a `PrimType`, a sealed trait whose cases are listed on this page. For each type, this page gives the constructors, the placeholder shown in the help, the input the user types, and any platform limits. `PrimType#validate(value)` parses a string and returns an `IO[String, A]` that fails with the messages on this page, which tests can use to check a type without a command.

## Summary

The table lists every value type, the constructors that use it, and the placeholder that the help and the usage line show for it:

| Value type        | `Args` constructor                                                         | `Options` constructor               | Scala type                 | Placeholder        |
| ----------------- | -------------------------------------------------------------------------- | ----------------------------------- | -------------------------- | ------------------ |
| Text              | `Args.text`, `Args.text(name)`                                             | `Options.text(name)`                | `String`                   | `text`             |
| Integer           | `Args.integer`, `Args.integer(name)`                                       | `Options.integer(name)`             | `BigInt`                   | `integer`          |
| Decimal           | `Args.decimal`, `Args.decimal(name)`                                       | `Options.decimal(name)`             | `BigDecimal`               | `decimal`          |
| Boolean           | `Args.bool`, `Args.bool(name)`                                             | `Options.boolean(name, ...)`        | `Boolean`                  | `boolean`          |
| Enumeration       | `Args.enumeration(cases*)`, `Args.enumeration(name)(cases*)`               | `Options.enumeration(name)(cases*)` | the case values            | `choice`           |
| Duration          | `Args.duration`, `Args.duration(name)`                                     | `Options.duration(name)`            | `java.time.Duration`       | `duration`         |
| Instant           | `Args.instant`, `Args.instant(name)`                                       | `Options.instant(name)`             | `java.time.Instant`        | `instant`          |
| Local date        | `Args.localDate(name)` only                                                | `Options.localDate(name)`           | `java.time.LocalDate`      | `date`             |
| Local date-time   | `Args.localDateTime`, `Args.localDateTime(name)`                           | `Options.localDateTime(name)`       | `java.time.LocalDateTime`  | `date-time`        |
| Local time        | `Args.localTime`, `Args.localTime(name)`                                   | `Options.localTime(name)`           | `java.time.LocalTime`      | `local-time`       |
| Month-day         | `Args.monthDay`, `Args.monthDay(name)`                                     | `Options.monthDay(name)`            | `java.time.MonthDay`       | `month-day`        |
| Offset date-time  | `Args.offsetDateTime`, `Args.offsetDateTime(name)`                         | `Options.offsetDateTime(name)`      | `java.time.OffsetDateTime` | `offset-date-time` |
| Offset time       | `Args.offsetTime`, `Args.offsetTime(name)`                                 | `Options.offsetTime(name)`          | `java.time.OffsetTime`     | `offset-time`      |
| Period            | `Args.period`, `Args.period(name)`                                         | `Options.period(name)`              | `java.time.Period`         | `period`           |
| Year              | `Args.year`, `Args.year(name)`                                             | `Options.year(name)`                | `java.time.Year`           | `year`             |
| Year-month        | `Args.yearMonth`, `Args.yearMonth(name)`                                   | `Options.yearMonth(name)`           | `java.time.YearMonth`      | `year-month`       |
| Zoned date-time   | `Args.zonedDateTime`, `Args.zonedDateTime(name)`                           | `Options.zonedDateTime(name)`       | `java.time.ZonedDateTime`  | `zoned-date-time`  |
| Zone ID           | `Args.zoneId`, `Args.zoneId(name)`                                         | `Options.zoneId(name)`              | `java.time.ZoneId`         | `zone-id`          |
| Zone offset       | `Args.zoneOffset`, `Args.zoneOffset(name)`                                 | `Options.zoneOffset(name)`          | `java.time.ZoneOffset`     | `zone-offset`      |
| File              | `Args.file`, `Args.file(exists)`, `Args.file(name, exists)`                | `Options.file(name, exists)`        | `java.nio.file.Path`       | `file`             |
| Directory         | `Args.directory`, `Args.directory(exists)`, `Args.directory(name, exists)` | `Options.directory(name, exists)`   | `java.nio.file.Path`       | `directory`        |
| File or directory | `Args.path`, `Args.path(name)`                                             | none                                | `java.nio.file.Path`       | `path`             |

An unnamed argument, such as `Args.integer`, uses the placeholder as its name: it appears as `<integer>` in the help. A named argument or an option uses its own name, and an option shows the placeholder after its name, as in `--count <integer>`. `Options#withPseudoName` replaces the placeholder of an option. `Args.localDate` has only the named form.

Options always need a name, so there is no unnamed `Options` constructor. There is also no `Options.path`; see [Custom Parameters From a PrimType](#custom-parameters-from-a-primtype) for an option that accepts a file or a directory.

To see the placeholder and the help sentence of a value type, we print the help of an option:

```scala
import zio.cli._

println(Options.localDate("since").helpDoc.toPlaintext(color = false))
// --since <date>
//   A date in ISO_LOCAL_DATE format, such as 2007-12-03
// 
//
```

## Text and Numbers

`PrimType.Text` accepts any string, including an empty one. `PrimType.Integer` reads the input with `BigInt(...)` and `PrimType.Decimal` with `BigDecimal(...)`, so both accept numbers of any size. An integer may have a sign, such as `-42`, and a decimal may use an exponent, such as `1e3`.

A value that cannot be converted fails with `<input> is not a <placeholder>.`, for example `ten is not a integer.`. The same message format is used by every type in this section and in [Dates and Times](#dates-and-times).

## Boolean

`PrimType.Bool` accepts `true`, `1`, `y`, `yes` and `on` for `true`, and `false`, `0`, `n`, `no` and `off` for `false`. The words are compared case-insensitively unless the app uses a case-sensitive [`CliConfig`](cli-config.md#case-sensitivity).

For a positional `Args.bool`, the user types one of these words. For an `Options.boolean` flag, the flag alone gives its value, and an explicit value is read only when it is exactly `true` or `false`; see [Boolean Options](options.md#boolean-options).

## Enumeration

`PrimType.Enumeration` takes pairs of the word the user types and the value it stands for. The word must match exactly, including its case, even when the app is otherwise case-insensitive. The help lists the words, and the usage line shows them as `debug | info`. Any other word fails with `Expected one of the following cases: debug, info`.

To define an enumeration option and print its help:

```scala
val level = Options.enumeration("level")(("debug", 0), ("info", 1), ("warn", 2))
```

```scala
println(level.helpDoc.toPlaintext(color = false))
// --level debug | info | warn
//   One of the following cases: debug, info, warn.
// 
//
```

## Dates and Times

The `java.time` types are parsed with the matching `parse` or `of` method of `java.time`, so they accept the ISO-8601 forms below. `Year` reads a plain integer, and `YearMonth` reads a year, a dash and a two-digit month. The help sentence of each type is the one ZIO CLI prints under the option or argument.

| Placeholder        | Example input that parses                 | Help sentence printed by ZIO CLI                                                                         |
| ------------------ | ----------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `duration`         | `PT1H30M`, `P1DT2H3M`                     | A time-based amount of time in the ISO-8601 format, such as 'P1DT2H3M'.                                  |
| `instant`          | `2007-12-03T10:15:30.00Z`                 | An instant in time in UTC format, such as 2007-12-03T10:15:30.00Z.                                       |
| `date`             | `2007-12-03`                              | A date in ISO_LOCAL_DATE format, such as 2007-12-03                                                      |
| `date-time`        | `2007-12-03T10:15:30`                     | A date-time without a time-zone in the ISO-8601 format, such as 2007-12-03T10:15:30.                     |
| `local-time`       | `10:15`, `10:15:30`                       | A time without a time-zone in the ISO-8601 format, such as 10:15:30.                                     |
| `month-day`        | `--12-03`                                 | A month-day in the ISO-8601 format such as 12-03.                                                        |
| `offset-date-time` | `2007-12-03T10:15:30+01:00`               | A date-time with an offset from UTC/Greenwich in the ISO-8601 format, such as 2007-12-03T10:15:30+01:00. |
| `offset-time`      | `10:15:30+01:00`                          | A time with an offset from UTC/Greenwich in the ISO-8601 format, such as 10:15:30+01:00.                 |
| `period`           | `P1Y2M3D`                                 | A date-based amount of time in the ISO-8601 format, such as 'P1Y2M3D'.                                   |
| `year`             | `2007`                                    | A year in the ISO-8601 format, such as 2007.                                                             |
| `year-month`       | `2007-12`                                 | A year-month in the ISO-8601 format, such as 2007-12.                                                    |
| `zoned-date-time`  | `2007-12-03T10:15:30+01:00[Europe/Paris]` | A date-time with a time-zone in the ISO-8601 format, such as 2007-12-03T10:15:30+01:00 Europe/Paris.     |
| `zone-id`          | `Europe/Paris`                            | A time-zone ID, such as Europe/Paris.                                                                    |
| `zone-offset`      | `+02:00`, `Z`                             | A time-zone offset from Greenwich/UTC, such as +02:00.                                                   |

:::caution
The help sentences for `month-day` and `zoned-date-time` show examples that do not parse. A month-day needs the leading dashes (`--12-03`, not `12-03`), and a zoned date-time needs the zone in square brackets (`2007-12-03T10:15:30+01:00[Europe/Paris]`). Add a correct example with `Args#??` or `Options#??` so your users see it.
:::

To add a correct example to the help of a month-day option:

```scala
val birthday = Options.monthDay("birthday") ?? "For example --12-03 for 3 December."
// birthday: Options[java.time.MonthDay] = Single(
//   name = "birthday",
//   aliases = Vector(),
//   primType = MonthDay,
//   description = Paragraph(
//     value = Text(value = "For example --12-03 for 3 December.")
//   ),
//   pseudoName = None
// )

println(birthday.helpDoc.toPlaintext(color = false))
// --birthday <month-day>
//   A month-day in the ISO-8601 format such as 12-03.
// 
//   For example --12-03 for 3 December.
// 
//
```

## Paths

Path parameters return a `java.nio.file.Path`. Two small types configure them:

`PathType` says which kind of path is expected: `PathType.File`, `PathType.Directory`, or `PathType.Either` when both are acceptable. `Exists` says whether the path must exist: `Exists.Yes`, `Exists.No`, or `Exists.Either` when it does not matter.

`PrimType.Path(pathType, shouldExist)` combines the two. `Args.file(name, exists = Exists.Yes)`, for example, wraps `PrimType.Path(PathType.File, Exists.Yes)`. A third parameter, `fileSystem`, is a `zio.cli.files.FileSystem` and defaults to `FileSystem.live`. `FileSystem` has four methods, `parsePath(path: String): IO[String, Path]`, `exists(path: Path): UIO[Boolean]`, `isDirectory(path: Path): UIO[Boolean]` and `isRegularFile(path: Path): UIO[Boolean]`, so a test can pass its own implementation.

The `exists` parameter of `Args.file`, `Args.directory`, `Options.file` and `Options.directory` defaults to `Exists.Either`. The help sentence follows the combination, as in `A file.`, `An existing file.` or `A directory that must not exist.`. Validation checks the path in two steps:

1. The existence rule: `Exists.Yes` rejects a missing path with `Path '<input>' must exist.`, and `Exists.No` rejects an existing path with `Path '<input>' must not exist.`.
2. The kind rule: if the path exists and `Exists.No` is not used, a file parameter rejects a directory with `Expected path '<input>' to be a regular file.`, and a directory parameter rejects a file with `Expected path '<input>' to be a directory.`.

A path that does not exist is accepted by `Exists.Either` without a kind check. `Args.path` uses `PathType.Either` and `Exists.Either`, so it accepts any path.

To accept only existing files and only new directories:

```scala
val input  = Args.file("input", exists = Exists.Yes)
val output = Options.directory("output", exists = Exists.No)
```

## Custom Parameters From a PrimType

You rarely need this; it is an advanced API for a value type that no constructor offers, such as an option that accepts an existing file or directory. `Args.Single(pseudoName, primType, description)` and `Options.Single(name, aliases, primType, description, pseudoName)` are public case classes, so we can wrap any `PrimType` directly:

```scala
val source: Options[java.nio.file.Path] =
  Options.Single("source", Vector("s"), PrimType.Path(PathType.Either, Exists.Yes))

val target: Args[java.nio.file.Path] =
  Args.Single(Some("target"), PrimType.Path(PathType.Either, Exists.Yes))
```

The help of `source` shows its alias, its placeholder and the sentence for the combination:

```scala
println(source.helpDoc.toPlaintext(color = false))
// (-s, --source <path>)
//   An existing file or directory.
// 
//
```

`PrimType` is sealed, so we cannot add a new case. To convert text into a type of our own, we use `Options#mapOrFail` or `Args#mapOrFail` instead; see [How to validate and convert a parameter's value](how-to-validate-input.md).

## Platform Support

Some value types depend on the file system, so their constructors differ between platforms:

| Constructor         | JVM | Scala Native | Scala.js                                           |
| ------------------- | --- | ------------ | -------------------------------------------------- |
| `Args.file`         | yes | yes          | no                                                 |
| `Args.directory`    | yes | yes          | no                                                 |
| `Args.path`         | yes | yes          | no                                                 |
| `Options.file`      | yes | yes          | compiles as `Options[String]`, rejects every value |
| `Options.directory` | yes | yes          | no                                                 |
| `Options.oauth2`    | yes | no           | no                                                 |

All other value types are available on every platform. On Scala.js there is no file system, so `Options.file` fails for every value the user passes with `FileSystem is not available in ScalaJS`. Only a default set with `Options#withDefault` can be used there. `Options.oauth2` is described in [Authorization with OAuth2](auth.md).
