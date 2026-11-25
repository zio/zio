# Process Large JSON as a Stream

> Read a large JSON array or JSON Lines file one value at a time, skip bad lines, and write streams of values back to files, with ZIO Streams on the JVM.

Goal: read and write JSON files that are too large to load into memory, one value at a time, with [ZIO Streams](https://zio.dev/reference/stream/).

The steps follow one data set through a round trip: a large JSON array export is read element by element, written out as [JSON Lines](https://jsonlines.org/) (one value per line), read back while skipping damaged lines, and written as a JSON array again. Each step also works on its own. The [Streaming API](../reference/streaming.md) reference lists every method used here, with its exact output and error behaviour.

## What You Need

The streaming API is part of the core module, but it exists on the JVM only:

```scala
libraryDependencies += "dev.zio" %% "zio-json" % "1.1.3"
```

The guide assumes you know how to derive a codec for a case class (see [Getting Started](../getting-started.md)) and how to run a `ZStream`. All steps use these imports, an `Event` type with a codec, and a small helper that runs an effect so this page can show results:

```scala
import zio._
import zio.stream._
import zio.json._

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

final case class Event(id: Int, kind: String)

object Event {
  implicit val codec: JsonCodec[Event] = DeriveJsonCodec.gen[Event]
}

def runSync[A](effect: ZIO[Any, Throwable, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
  }

def readText(path: Path): String =
  new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
```

In a real program the export already exists. For this page we write a small one to a temporary directory, and name the files the later steps write:

```scala
val dir: Path = Files.createTempDirectory("events")

val exportFile: Path  = dir.resolve("events.json")
val linesFile: Path   = dir.resolve("events.jsonl")
val damagedFile: Path = dir.resolve("damaged.jsonl")
val arrayFile: Path   = dir.resolve("good-events.json")

Files.write(
  exportFile,
  """[
    |  {"id": 1, "kind": "created"},
    |  {"id": 2, "kind": "updated"},
    |  {"id": 3, "kind": "deleted"}
    |]""".stripMargin.getBytes(StandardCharsets.UTF_8)
)
```

## 1. Read a Large JSON Array One Element at a Time

`ZStream.fromPath` reads the file as bytes, `ZPipeline.decodeCharsWith` turns the bytes into characters, and `JsonDecoder#decodeJsonPipeline` decodes the elements of the array one by one. Its default delimiter, `JsonStreamDelimiter.Array`, expects one JSON array:

```scala
val exported: ZStream[Any, Throwable, Event] =
  ZStream
    .fromPath(exportFile)
    .via(ZPipeline.decodeCharsWith(StandardCharsets.UTF_8))
    .via(JsonDecoder[Event].decodeJsonPipeline())
```

The stream holds only the elements it is working on, so it can filter, count or transform an array of any size. To count the `deleted` events:

```scala
runSync(exported.filter(_.kind == "deleted").runCount)
// res1: Long = 1L
```

If the file holds one large object rather than an array, decode it with `JsonDecoder#decodeJsonStreamInput(ZStream.fromPath(exportFile))` instead. It reads the bytes incrementally and returns the single value.

## 2. Write a Stream of Values as JSON Lines

`writeJsonLinesAs`, from the `zio.json` package, encodes every value of a stream on its own line and writes the result to a file as UTF-8. It creates the file or replaces its contents:

```scala
runSync(writeJsonLinesAs(linesFile, exported))

println(readText(linesFile))
// {"id":1,"kind":"created"}
// {"id":2,"kind":"updated"}
// {"id":3,"kind":"deleted"}
//
```

:::warning[Live consumers]
The stream encoders collect output in a 4096-character buffer, so a value written to a socket or a server-sent events response can wait until about 4 KB of later output arrives. When each value must reach the consumer as soon as it is produced, encode the values one at a time, for example `stream.map(_.toJson + "\n")` for a `stream` of values. See [Output Format and Buffering](../reference/streaming.md#output-format-and-buffering).
:::

## 3. Read JSON Lines Back

`readJsonLinesAs` reads a JSON Lines file into a stream of values:

```scala
runSync(readJsonLinesAs[Event](linesFile).runCollect)
// res4: Chunk[Event] = IndexedSeq(
//   Event(id = 1, kind = "created"),
//   Event(id = 2, kind = "updated"),
//   Event(id = 3, kind = "deleted")
// )
```

`readJsonLinesAs` stops the stream at the first line that does not decode, and values decoded just before that line can be lost with it (see [Errors in a Decoding Pipeline](../reference/streaming.md#errors-in-a-decoding-pipeline)). For files that may contain bad lines, use the next step instead.

## 4. Skip Bad Lines Instead of Failing

Log files and exports from other systems sometimes contain a damaged line. Here the line for event 2 has lost its `kind` field, the line for event 4 is cut short, and one line is blank:

```scala
Files.write(
  damagedFile,
  """{"id":1,"kind":"created"}
    |{"id":2}
    |
    |{"id":3,"kind":"deleted"}
    |{"id":4,"kind":
    |{"id":5,"kind":"restored"}
    |""".stripMargin.getBytes(StandardCharsets.UTF_8)
)
```

To keep going past a bad line, split the text into lines yourself with `ZPipeline.utf8Decode` and `ZPipeline.splitLines`, drop blank lines, and decode each line on its own with `fromJson`. Every line then gives an `Either`, and a bad line does not affect the others:

```scala
val decodedLines: ZStream[Any, Throwable, Either[String, Event]] =
  ZStream
    .fromPath(damagedFile)
    .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
    .filter(_.trim.nonEmpty)
    .map(_.fromJson[Event])
```

Running the stream shows one result per non-blank line:

```scala
runSync(decodedLines.runCollect)
// res6: Chunk[Either[String, Event]] = IndexedSeq(
//   Right(value = Event(id = 1, kind = "created")),
//   Left(value = ".kind(missing)"),
//   Right(value = Event(id = 3, kind = "deleted")),
//   Left(value = "Unexpected end of input"),
//   Right(value = Event(id = 5, kind = "restored"))
// )
```

To log the errors and keep only the good values, tap the `Left` results and then collect the `Right` results:

```scala
val goodEvents: ZStream[Any, Throwable, Event] =
  decodedLines
    .tap {
      case Left(error) => Console.printLine(s"skipping bad line: $error")
      case Right(_)    => ZIO.unit
    }
    .collect { case Right(event) => event }
```

The stream prints a message for each bad line and keeps the three good events:

```scala
runSync(goodEvents.runCollect)
// skipping bad line: .kind(missing)
// skipping bad line: Unexpected end of input
// res7: Chunk[Event] = IndexedSeq(
//   Event(id = 1, kind = "created"),
//   Event(id = 3, kind = "deleted"),
//   Event(id = 5, kind = "restored")
// )
```

This approach uses only `fromJson`, so it also works on Scala.js and Scala Native with a stream source other than a file.

## 5. Write a Stream of Values as a JSON Array File

Some consumers expect one JSON document rather than JSON Lines. `JsonEncoder#encodeJsonArrayPipeline` writes the stream as a single array, `ZPipeline.encodeCharsWith` turns the characters into UTF-8 bytes, and `ZSink.fromPath` writes them to a file. Here the input is `goodEvents` from step 4, so the damaged file is read again and its bad lines are logged again; the sink returns the number of bytes written:

```scala
runSync(
  goodEvents
    .via(JsonEncoder[Event].encodeJsonArrayPipeline)
    .via(ZPipeline.encodeCharsWith(StandardCharsets.UTF_8))
    .run(ZSink.fromPath(arrayFile))
)
// skipping bad line: .kind(missing)
// skipping bad line: Unexpected end of input
// res8: Long = 80L

println(readText(arrayFile))
// [{"id":1,"kind":"created"},{"id":3,"kind":"deleted"},{"id":5,"kind":"restored"}]
```

The output is compact; the stream encoders have no pretty-printed form. The file can be read back with step 1.

## See Also

- [Streaming API](../reference/streaming.md) for every streaming method, the delimiters, the file helpers and the exact error behaviour.
- [JsonEncoder](../reference/json-encoder.md#streaming) and [JsonDecoder](../reference/json-decoder.md#streaming) for the type classes that carry the streaming methods.
- [Errors](../reference/errors.md) for the error messages a failed stream carries.
