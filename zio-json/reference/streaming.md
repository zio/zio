# Streaming API

> Reference for the JVM-only ZIO Streams API in ZIO JSON: stream encoders, decoders, pipelines, JsonStreamDelimiter, file helpers and error behaviour.

The streaming API connects ZIO JSON codecs to [ZIO Streams](https://zio.dev/reference/stream/). It encodes values into a `ZStream` of characters and decodes a `ZStream` of characters or bytes back into values, so a program can read or write a large JSON document, or a long sequence of documents, without holding all of the text in memory. It supports three shapes of input and output: a single JSON value, a JSON array read or written one element at a time, and [JSON Lines](https://jsonlines.org/) (one value per line).

The API is a set of methods on `JsonEncoder` and `JsonDecoder`, one small type, `JsonStreamDelimiter`, and a few file helpers in the `zio.json` package object:

```scala
trait JsonEncoder[A] {
  def encodeJsonStream(a: A): ZStream[Any, Throwable, Char]
  lazy val encodeJsonLinesPipeline: ZPipeline[Any, Throwable, A, Char]
  lazy val encodeJsonArrayPipeline: ZPipeline[Any, Throwable, A, Char]
}

trait JsonDecoder[A] {
  def decodeJsonStream[R](stream: ZStream[R, Throwable, Char]): ZIO[R, Throwable, A]
  def decodeJsonStreamInput[R](
    stream: ZStream[R, Throwable, Byte],
    charset: Charset = StandardCharsets.UTF_8
  ): ZIO[R, Throwable, A]
  def decodeJsonPipeline(
    delimiter: JsonStreamDelimiter = JsonStreamDelimiter.Array
  ): ZPipeline[Any, Throwable, Char, A]
}

sealed trait JsonStreamDelimiter
object JsonStreamDelimiter {
  case object Newline extends JsonStreamDelimiter
  case object Array   extends JsonStreamDelimiter
}
```

:::note[JVM only]
Every method on this page exists only on the JVM. The `JsonEncoder` and `JsonDecoder` methods come from the `JsonEncoderPlatformSpecific` and `JsonDecoderPlatformSpecific` traits, and the file helpers from `JsonPackagePlatformSpecific`. On Scala.js and Scala Native these traits are empty. `JsonStreamDelimiter` is defined on every platform, but only the JVM `JsonDecoder#decodeJsonPipeline` uses it.
:::

## Usage

The examples on this page share one type and a helper that runs an effect and returns its result:

```scala
import zio._
import zio.stream._
import zio.json._

final case class Event(id: Int, kind: String)

object Event {
  implicit val codec: JsonCodec[Event] = DeriveJsonCodec.gen[Event]
}

def runSync[A](effect: ZIO[Any, Throwable, A]): A =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
  }

val events: ZStream[Any, Nothing, Event] =
  ZStream(Event(1, "created"), Event(2, "updated"), Event(3, "deleted"))
```

A round trip through JSON Lines encodes a stream of events with one pipeline and decodes the text with another:

```scala
val jsonLines: String =
  runSync(events.via(JsonEncoder[Event].encodeJsonLinesPipeline).runCollect.map(_.mkString))
// jsonLines: String = """{"id":1,"kind":"created"}
// {"id":2,"kind":"updated"}
// {"id":3,"kind":"deleted"}
// """

runSync(
  ZStream
    .fromIterable(jsonLines)
    .via(JsonDecoder[Event].decodeJsonPipeline(JsonStreamDelimiter.Newline))
    .runCollect
)
// res1: Chunk[Event] = IndexedSeq(
//   Event(id = 1, kind = "created"),
//   Event(id = 2, kind = "updated"),
//   Event(id = 3, kind = "deleted")
// )
```

## Installation

The streaming API is part of the core `zio-json` artifact, which depends on `zio-streams`. No extra module is needed:

```scala
libraryDependencies += "dev.zio" %% "zio-json" % "1.1.3"
```

## Encoding

The encoding side has one method that turns a single value into a character stream and two pipelines that turn a stream of values into a character stream. A `ZPipeline[Any, Throwable, A, Char]` is applied with `ZStream#via`.

### Encoding a Single Value

`JsonEncoder#encodeJsonStream` returns the JSON text of one value as a `ZStream[Any, Throwable, Char]`:

```scala
trait JsonEncoder[A] {
  def encodeJsonStream(a: A): ZStream[Any, Throwable, Char]
}
```

To collect the characters of one event into a `String`:

```scala
runSync(JsonEncoder[Event].encodeJsonStream(Event(1, "created")).runCollect.map(_.mkString))
// res2: String = "{\"id\":1,\"kind\":\"created\"}"
```

### Encoding JSON Lines

`JsonEncoder#encodeJsonLinesPipeline` writes each value followed by a newline character (`\n`), including the last one. An empty input stream produces no characters:

```scala
trait JsonEncoder[A] {
  lazy val encodeJsonLinesPipeline: ZPipeline[Any, Throwable, A, Char]
}
```

The `jsonLines` value from the [Usage](#usage) example holds three lines, each ending with `\n`:

```scala
jsonLines.count(_ == '\n')
// res3: Int = 3
```

### Encoding a JSON Array

`JsonEncoder#encodeJsonArrayPipeline` writes the whole stream as one JSON array: an opening `[`, the values separated by commas, and a closing `]`. An empty input stream produces `[]`:

```scala
trait JsonEncoder[A] {
  lazy val encodeJsonArrayPipeline: ZPipeline[Any, Throwable, A, Char]
}
```

To encode the three events, and then an empty stream, as arrays:

```scala
runSync(events.via(JsonEncoder[Event].encodeJsonArrayPipeline).runCollect.map(_.mkString))
// res4: String = "[{\"id\":1,\"kind\":\"created\"},{\"id\":2,\"kind\":\"updated\"},{\"id\":3,\"kind\":\"deleted\"}]"

runSync(ZStream.empty.via(JsonEncoder[Event].encodeJsonArrayPipeline).runCollect.map(_.mkString))
// res5: String = "[]"
```

### Output Format and Buffering

The three encoders share one implementation, so they format and emit output the same way:

- The output is always compact, written without indentation. There is no pretty-printed streaming form.
- The characters go through a `java.io.BufferedWriter` with a buffer of `ZStream.DefaultChunkSize` (4096) characters. Characters reach the output stream only when the buffer fills, in whole 4096-character blocks, or when the input stream ends and the rest is flushed. The pipelines can also emit empty chunks in between.
- Writing happens inside `ZIO.attemptBlocking`, on ZIO's blocking thread pool.

The buffering affects slow or never-ending sources, such as server-sent events or a log tail: a value can wait in the buffer until about 4 KB of later output arrives. An event such as `{"id":1,"kind":"created"}` takes 26 characters with its newline, so about 150 events fill one block. To send each value as soon as it is produced, encode the values one at a time instead, for example with `events.map(_.toJson + "\n")`.

## Decoding

The decoding side has two methods that read one value and one pipeline that reads many values.

### Decoding a Single Value

`JsonDecoder#decodeJsonStream` decodes one value from a stream of characters. `JsonDecoder#decodeJsonStreamInput` decodes one value from a stream of bytes, using the given `Charset` (UTF-8 by default):

```scala
trait JsonDecoder[A] {
  def decodeJsonStream[R](stream: ZStream[R, Throwable, Char]): ZIO[R, Throwable, A]
  def decodeJsonStreamInput[R](
    stream: ZStream[R, Throwable, Byte],
    charset: Charset = StandardCharsets.UTF_8
  ): ZIO[R, Throwable, A]
}
```

Both turn the stream into a `java.io.Reader` (through an `InputStream` for bytes) and decode from it on ZIO's blocking thread pool, so the input is read incrementally. To decode one event from characters and another from UTF-8 bytes:

```scala
runSync(JsonDecoder[Event].decodeJsonStream(ZStream.fromIterable("""{"id":4,"kind":"archived"}""")))
// res6: Event = Event(id = 4, kind = "archived")

runSync(
  JsonDecoder[Event].decodeJsonStreamInput(
    ZStream.fromChunk(Chunk.fromArray("""{"id":5,"kind":"restored"}""".getBytes("UTF-8")))
  )
)
// res7: Event = Event(id = 5, kind = "restored")
```

Both methods stop after the first value. They do not read or check the rest of the stream, so text after the value, even a second value or invalid JSON, is not an error:

```scala
runSync(JsonDecoder[Event].decodeJsonStream(ZStream.fromIterable("""{"id":6,"kind":"first"} not json""")))
// res8: Event = Event(id = 6, kind = "first")
```

### Decoding Many Values

`JsonDecoder#decodeJsonPipeline` returns a pipeline from characters to values. It decodes one value at a time and passes the values downstream as they are decoded, so a long input never has to fit in memory:

```scala
trait JsonDecoder[A] {
  def decodeJsonPipeline(
    delimiter: JsonStreamDelimiter = JsonStreamDelimiter.Array
  ): ZPipeline[Any, Throwable, Char, A]
}
```

The `delimiter` argument selects how the values are laid out in the input. The decoding runs in a background fiber on ZIO's blocking thread pool, and the pipeline passes the decoded values on in batches: up to `ZStream.DefaultChunkSize` (4096) values for each chunk of input, and all remaining values when the input ends.

The pipeline takes characters. For a byte source, such as a file, put a character decoder in front of it, for example `ZPipeline.utf8Decode` followed by a step that splits the strings into characters, or `ZPipeline.decodeCharsWith(StandardCharsets.UTF_8)`.

### `JsonStreamDelimiter`

`JsonStreamDelimiter` has two cases, which choose the input layout for `JsonDecoder#decodeJsonPipeline`:

| Case                           | Input layout                                | Default |
| ------------------------------ | ------------------------------------------- | ------- |
| `JsonStreamDelimiter.Array`    | One JSON array; each element is one value   | Yes     |
| `JsonStreamDelimiter.Newline`  | JSON Lines; one value per line              | No      |

With `JsonStreamDelimiter.Array`, the input must start with `[` (leading whitespace is allowed). An empty array gives an empty stream. Between elements the pipeline expects a `,` or the closing `]`:

```scala
runSync(
  ZStream
    .fromIterable("""[{"id":1,"kind":"created"}, {"id":2,"kind":"updated"}]""")
    .via(JsonDecoder[Event].decodeJsonPipeline())
    .runCollect
)
// res9: Chunk[Event] = IndexedSeq(
//   Event(id = 1, kind = "created"),
//   Event(id = 2, kind = "updated")
// )
```

With `JsonStreamDelimiter.Newline`, the pipeline consumes one `\n` or `\r\n` after each value. The decoder skips whitespace before a value, so blank lines are accepted, and so are several values on one line separated by spaces:

```scala
runSync(
  ZStream
    .fromIterable("{\"id\":1,\"kind\":\"created\"}\r\n\r\n{\"id\":2,\"kind\":\"updated\"} {\"id\":3,\"kind\":\"deleted\"}")
    .via(JsonDecoder[Event].decodeJsonPipeline(JsonStreamDelimiter.Newline))
    .runCollect
)
// res10: Chunk[Event] = IndexedSeq(
//   Event(id = 1, kind = "created"),
//   Event(id = 2, kind = "updated"),
//   Event(id = 3, kind = "deleted")
// )
```

A trailing newline after the last value is optional. An empty input gives an empty stream with either delimiter.

## File Helpers

The `zio.json` package object (through `JsonPackagePlatformSpecific`) has helpers that read and write files of JSON Lines. They combine the pipelines above with `ZStream.fromPath`, `ZSink.fromPath` and UTF-8 conversion:

```scala
package object json {
  def readJsonLinesAs[A: JsonDecoder](file: File): ZStream[Any, Throwable, A]
  def readJsonLinesAs[A: JsonDecoder](path: Path): ZStream[Any, Throwable, A]
  def readJsonLinesAs[A: JsonDecoder](path: String): ZStream[Any, Throwable, A]
  def readJsonLinesAs[A: JsonDecoder](url: URL): ZStream[Any, Throwable, A]

  def readJsonAs(file: File): ZStream[Any, Throwable, ast.Json]
  def readJsonAs(path: Path): ZStream[Any, Throwable, ast.Json]
  def readJsonAs(path: String): ZStream[Any, Throwable, ast.Json]
  def readJsonAs(url: URL): ZStream[Any, Throwable, ast.Json]

  def writeJsonLinesAs[R, A: JsonEncoder](file: File, stream: ZStream[R, Throwable, A]): RIO[R, Unit]
  def writeJsonLinesAs[R, A: JsonEncoder](path: Path, stream: ZStream[R, Throwable, A]): RIO[R, Unit]
  def writeJsonLinesAs[R, A: JsonEncoder](path: String, stream: ZStream[R, Throwable, A]): RIO[R, Unit]

  def writeJsonLines[R](file: File, stream: ZStream[R, Throwable, ast.Json]): RIO[R, Unit]
  def writeJsonLines[R](path: Path, stream: ZStream[R, Throwable, ast.Json]): RIO[R, Unit]
  def writeJsonLines[R](path: String, stream: ZStream[R, Throwable, ast.Json]): RIO[R, Unit]
}
```

The helpers behave as follows:

- `readJsonLinesAs` reads the file (or URL) as UTF-8 and decodes it with `decodeJsonPipeline(JsonStreamDelimiter.Newline)`. A `String` argument is a file path, converted with `Paths.get`.
- `readJsonAs` is `readJsonLinesAs[ast.Json]`. Despite its name, it reads JSON Lines, not one JSON document, so a file that holds a single document gives a stream of one `Json` value.
- `writeJsonLinesAs` encodes the stream with `JsonEncoder#encodeJsonLinesPipeline`, converts the characters to UTF-8 and writes them with `ZSink.fromPath`, which creates the file or replaces its contents.
- `writeJsonLines` is `writeJsonLinesAs` for a stream of `ast.Json` values.

There is no helper for a file that holds one JSON array. Combine `JsonDecoder#decodeJsonPipeline` or `JsonEncoder#encodeJsonArrayPipeline` with `ZStream.fromPath` and `ZSink.fromPath` yourself, as [Process Large JSON as a Stream](../how-to/streaming.md) shows.

The example below writes to a temporary file:

```scala
val file: java.nio.file.Path = java.nio.file.Files.createTempFile("events", ".jsonl")
```

Writing the three events to it and reading them back gives the same events:

```scala
runSync(writeJsonLinesAs(file, events) *> readJsonLinesAs[Event](file).runCollect)
// res11: Chunk[Event] = IndexedSeq(
//   Event(id = 1, kind = "created"),
//   Event(id = 2, kind = "updated"),
//   Event(id = 3, kind = "deleted")
// )
```

## Errors

Every streaming method reports failures in the `Throwable` error channel. A decoding failure is a `java.lang.Exception` whose message is the rendered error path, in the same format as the `Left` from `fromJson` (see [Errors](errors.md)). Reading a file that does not exist fails with the I/O exception from the file system.

### Errors When Decoding a Single Value

`JsonDecoder#decodeJsonStream` and `JsonDecoder#decodeJsonStreamInput` fail the effect when the value does not decode. When the input ends in the middle of the value, the message is `unexpected end of input`, in lower case:

```scala
runSync(JsonDecoder[Event].decodeJsonStream(ZStream.fromIterable("""{"id":7}""")).either)
// res12: Either[Throwable, Event] = Left(
//   value = java.lang.Exception: .kind(missing)
// )

runSync(JsonDecoder[Event].decodeJsonStream(ZStream.fromIterable("""{"id":7,"ki""")).either)
// res13: Either[Throwable, Event] = Left(
//   value = java.lang.Exception: unexpected end of input
// )
```

### Errors in a Decoding Pipeline

In the current implementation, `JsonDecoder#decodeJsonPipeline` behaves as follows when the input is not valid:

- The first value that fails to decode fails the whole stream. The values after it are not decoded, and a failed stream cannot resume, so `ZStream#catchAll` can only replace the rest of the stream.
- Values decoded before the failing one are not guaranteed to be emitted. The pipeline passes values downstream in batches, and when a batch contains the failure, the values in that batch are dropped.
- When the input ends in the middle of the last value, the stream ends without an error, and the incomplete value is left out. With `JsonStreamDelimiter.Array`, a missing closing `]` is not reported either.
- With `JsonStreamDelimiter.Array`, input that does not start with `[` fails with a message such as `(expected '[' got '{')`.

A missing field in the second of three lines fails the stream:

```scala
val withBadLine: String =
  """{"id":1,"kind":"created"}
    |{"id":2}
    |{"id":3,"kind":"deleted"}
    |""".stripMargin
// withBadLine: String = """{"id":1,"kind":"created"}
// {"id":2}
// {"id":3,"kind":"deleted"}
// """

runSync(
  ZStream
    .fromIterable(withBadLine)
    .via(JsonDecoder[Event].decodeJsonPipeline(JsonStreamDelimiter.Newline))
    .runCollect
    .either
)
// res14: Either[Throwable, Chunk[Event]] = Left(
//   value = java.lang.Exception: .kind(missing)
// )
```

A last line cut short ends the stream after the complete values:

```scala
runSync(
  ZStream
    .fromIterable("{\"id\":1,\"kind\":\"created\"}\n{\"id\":2,\"ki")
    .via(JsonDecoder[Event].decodeJsonPipeline(JsonStreamDelimiter.Newline))
    .runCollect
)
// res15: Chunk[Event] = IndexedSeq(Event(id = 1, kind = "created"))
```

To keep going past bad lines in JSON Lines input, split the text with `ZPipeline.splitLines` and decode each line with `fromJson`, as [Process Large JSON as a Stream](../how-to/streaming.md#4-skip-bad-lines-instead-of-failing) shows. That technique uses only `fromJson`, so it also works on Scala.js and Scala Native.

## See Also

- [Process Large JSON as a Stream](../how-to/streaming.md) for task-oriented examples with files.
- [JsonEncoder](json-encoder.md#streaming) and [JsonDecoder](json-decoder.md#streaming) for the type classes that carry these methods.
- [Errors](errors.md) for the error message format.
- [Performance](../explanation/performance.md) for when to decode from a stream instead of from a `String` or bytes.
