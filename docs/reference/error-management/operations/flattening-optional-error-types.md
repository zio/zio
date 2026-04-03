---
id: flattening-optional-error-types
title: "Flattening Optional Error Types"
---

If we have an optional error of type `E` in the error channel, we can flatten it to the `E` type using the `ZIO#flattenErrorOption` operator:

```scala mdoc:compile-only
import zio._

def parseInt(input: String): ZIO[Any, Option[String], Int] =
  if (input.isEmpty)
    ZIO.fail(Some("empty input"))
  else
    try {
      ZIO.succeed(input.toInt)
    } catch {
      case _: NumberFormatException => ZIO.fail(None)
    }

def flattenedParseInt(input: String): ZIO[Any, String, Int] =
  parseInt(input).flattenErrorOption("non-numeric input")

val r1: ZIO[Any, String, Int] = flattenedParseInt("zero")
val r2: ZIO[Any, String, Int] = flattenedParseInt("")
val r3: ZIO[Any, String, Int] = flattenedParseInt("123")
```

