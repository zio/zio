# Performance

> How ZIO JSON's benchmarks are set up, what the published results mean, and practical tips for fast encoding and decoding.

ZIO JSON decodes JSON text straight into your data types and encodes them straight to text, without building an intermediate syntax tree. This page describes the benchmark suite that measures it, how to read the published numbers, and what you can do in your own code to encode and decode faster.

## The Benchmark Suite

The benchmarks are [JMH](https://github.com/openjdk/jmh) benchmarks in [`zio-json/jvm/src/jmh`](https://github.com/zio/zio-json/tree/series/2.x/zio-json/jvm/src/jmh/scala/zio/json). The main ones compare ZIO JSON with [Circe](https://circe.github.io/circe/) and [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala) on four standard datasets:

| Benchmark class           | Dataset                  | What it stresses                              |
| ------------------------- | ------------------------ | --------------------------------------------- |
| `GoogleMapsAPIBenchmarks` | Google Maps API response | Arrays and number parsing                     |
| `TwitterAPIBenchmarks`    | Twitter API response     | Nested case classes with many fields          |
| `GeoJSONBenchmarks`       | GeoJSON document         | Nested sealed traits that use a discriminator |
| `SyntheticBenchmarks`     | Generated recursive data | Nested recursive types                        |

Each class has `decode…` and `encode…` methods per library. `GoogleMapsAPIBenchmarks` also has `…Attack…` methods that decode the malicious payloads described on the [Security](security.md) page. Other classes measure single features: collections, decoding from bytes (`ChunkDecoderBenchmarks`), nesting depth, `UUID` parsing, `orElse` decoders and the number parser.

The suite runs on the JVM with `NeoJmhPlugin`, a rewrite of [sbt-jmh](https://github.com/sbt/sbt-jmh) defined in the build (`project/NeoJmhPlugin.scala`) that adds a `Jmh` configuration to the `zioJsonJVM` project. To run it on your own hardware, with the GC profiler that reports memory use:

```bash
sbt "zioJsonJVM/Jmh/run -prof gc"
```

To run only some benchmarks, add a regular expression that matches their names, for example `sbt "zioJsonJVM/Jmh/run -prof gc GoogleMaps.*"`.

There are two main factors to consider when comparing the performance of JSON libraries: memory usage and operations per second. The benchmarks run one thread at a time, but in a real server situation there are multiple threads, each consuming resources. JMH reports throughput as `ops/sec` (higher is better). The GC profiler reports the allocation rate as `MB/sec` (lower is better). A better metric for memory usage might be `MB` per decode or encode, since it can be misleading to have the same `MB/sec` but be processing more JSON: the library that allocates the least memory is likely to have the highest throughput.

## Historical Results

The tables below were published with the first version of ZIO JSON in July 2020. They compare ZIO JSON with Circe and Play JSON, measured on the hardware and library versions of that time.

:::note
These numbers cannot be reproduced with the current suite. The Play JSON benchmarks were removed in January 2025 ([#1220](https://github.com/zio/zio-json/pull/1220)), when jsoniter-scala encoding benchmarks were added, and all three libraries have changed a lot since 2020. Run the suite yourself to get numbers for your hardware and versions.
:::

<!-- zioJsonJVM/Jmh/run -prof gc GoogleMaps.*Success1 -->
<!-- zioJsonJVM/Jmh/run -prof gc GoogleMaps.*encode* -->

On the Google Maps API dataset:

```
       Decoding                    | Encoding
       ops/sec       MB/sec        | ops/sec      MB/sec
zio    15761 ± 283   1633 ± 29     | 14289 ±  84  2214 ± 12
circe   8832 ± 269   1816 ± 55     | 11980 ± 142  2030 ± 24
play    5756 ±  47   2260 ± 19     |  6669 ± 160  2677 ± 64
```

<!-- zioJsonJVM/Jmh/run -prof gc Twitter.*Success1 -->
<!-- zioJsonJVM/Jmh/run -prof gc Twitter.*encode* -->

On the Twitter API dataset:

```
       Decoding                    | Encoding
       ops/sec       MB/sec        | ops/sec      MB/sec
zio    16989 ± 113    827 ±  6     | 23085 ± 641  1791 ± 50
circe  16010 ±  72   1349 ±  6     | 15664 ± 209  1627 ± 22
play    5256 ± 165   1231 ± 39     | 15580 ± 314  2260 ± 45
```

<!-- zioJsonJVM/Jmh/run -prof gc GeoJSON.*Success1 -->
<!-- zioJsonJVM/Jmh/run -prof gc GeoJSON.*encode* -->

On the GeoJSON dataset:

```
       Decoding                    | Encoding
       ops/sec       MB/sec        | ops/sec       MB/sec
zio    17104 ± 155   2768 ± 25     | 5372 ± 26      817 ±  4
circe   8388 ± 118   2879 ± 41     | 4762 ± 47      592 ±  6
play     704 ±   9   3946 ± 55     | 2587 ± 24     1091 ± 10
```

<!-- zioJsonJVM/Jmh/run -prof gc Synthetic.*Success -->
<!-- zioJsonJVM/Jmh/run -prof gc Synthetic.*encode* -->

On the synthetic dataset:

```
       Decoding                    | Encoding
       ops/sec       MB/sec        | ops/sec       MB/sec
zio    59099 ± 1307  2108 ± 46     | 32048 ±  240  2573 ± 19
circe  19609 ±  370  2873 ± 53     | 13830 ±  109  1730 ± 14
play    9001 ±  182  3348 ± 67     | 14529 ±  200  3533 ± 48
```

In these 2020 results, ZIO JSON has the highest ops/sec in every table: from about 1.1x to 3x the ops/sec of Circe, and from about 1.5x to 24x the ops/sec of Play JSON. On MB/sec it allocates the least memory when decoding, but it trails Circe in all four encoding columns, where Circe allocates less memory per second.

## Encoding Faster

Most encoders never need tuning, but a few details of how encoding works matter when you write encoders by hand or encode large volumes.

### Reusable Buffers Behind `toJson`

`toJson` and `toJsonPretty` call `JsonEncoder#encodeJson`. It writes into a character buffer taken from a per-thread pool and returns the buffer's contents as a `String`, so repeated encoding on the same thread reuses the buffer instead of allocating a new one each time. The pool is held through a weak reference, so the garbage collector can reclaim it when memory is short.

Inside a hand-written encoder, write to the `Write` that `JsonEncoder#unsafeEncode` receives, or delegate to another encoder's `JsonEncoder#unsafeEncode`. Calling `toJson` inside `JsonEncoder#unsafeEncode` encodes into a second buffer and creates an intermediate `String` that is then copied into the first. [Writing an Encoder by Hand](../reference/json-encoder.md#writing-an-encoder-by-hand) shows the pattern.

### Compact Versus Pretty Output

`toJsonPretty` writes a newline and indentation before every field and array element, and a space on each side of the `:`. The output is much larger than the compact form: for a list of small objects, it is roughly twice as many characters, and the encoder spends correspondingly longer writing them. Use `toJson` for data that machines read, and keep `toJsonPretty` for logs and files that people read.

### Converting to the AST Directly

`JsonEncoder#toJsonAST` converts a value to the `Json` AST. The default implementation encodes the value to text and then parses that text back into a `Json`. The built-in encoders, the derived encoders and encoders built with `JsonEncoder#contramap` override it to build the `Json` directly. A hand-written encoder that implements only `JsonEncoder#unsafeEncode` uses the slow default, and so does every derived encoder that contains it as a field, whenever `JsonEncoder#toJsonAST` runs. Either build the encoder from an existing one with `JsonEncoder#contramap`, or override `JsonEncoder#toJsonAST` as shown in [Overriding `toJsonAST`](../reference/json-encoder.md#overriding-tojsonast).

### Writing Straight to a `java.io.Writer`

`toJson` always builds the whole document as a `String`. When the output goes to a `java.io.Writer`, such as a file or a servlet response, `zio.json.internal.WriteWriter` adapts the writer to the `Write` interface, so `JsonEncoder#unsafeEncode` can write into it directly. The JVM streaming encoders use it internally:

```scala
import java.io.StringWriter

import zio.json._
import zio.json.internal.WriteWriter

final case class Point(x: Int, y: Int)

object Point {
  implicit val codec: JsonCodec[Point] = DeriveJsonCodec.gen[Point]
}

val writer = new StringWriter()

JsonEncoder[Point].unsafeEncode(Point(1, 2), None, new WriteWriter(writer))
```

The `StringWriter` now holds the encoded JSON:

```scala
writer.toString
// res2: String = "{\"x\":1,\"y\":2}"
```

`WriteWriter` passes a `String` to the underlying writer in one call, but every other character in a separate call, so wrap an unbuffered writer in a `java.io.BufferedWriter`. Everything in `zio.json.internal` is a low-level API without a compatibility guarantee. For ZIO streams, prefer the streaming encoders described in [Streaming API](../reference/streaming.md).

## Decoding Faster

The decoder reads input once, from left to right, and builds your data types as it goes. These choices keep that path short.

### Decoding From Bytes

An HTTP body or a file usually arrives as bytes. Converting the bytes to a `String` first (`new String(bytes, UTF_8)`) copies the whole payload. `JsonDecoder#decodeJson` has overloads for `Chunk[Byte]` and `Array[Byte]` that decode UTF-8 while parsing, without that copy, and `fromJson` works on both types:

```scala
import java.nio.charset.StandardCharsets.UTF_8

import zio.Chunk

val bytes: Chunk[Byte] = Chunk.fromArray("""{"x":1,"y":2}""".getBytes(UTF_8))
// bytes: Chunk[Byte] = IndexedSeq(
//   123,
//   34,
//   120,
//   34,
//   58,
//   49,
//   44,
//   34,
//   121,
//   34,
//   58,
//   50,
//   125
// )

bytes.fromJson[Point]
// res3: Either[String, Point] = Right(value = Point(x = 1, y = 2))
```

`ChunkDecoderBenchmarks` measures this path against decoding a `String` and against copying the bytes into a `String` first. See [`decodeJson`](../reference/json-decoder.md#decodejson) for the full list of overloads.

### Decoding Into Your Own Types

Decoding straight into a case class is the fastest path. The derived decoder matches field names without building a map, and skips fields it does not know without building values for them. Decoding into `Json` builds a tree of every value in the document, and decoding into `Map[String, V]` builds a hash map of every key, so use them only when the shape of the data is not known in advance.

When you do decode into `Json` first and convert it with `Json#as` or `JsonDecoder#fromJsonAST`, the conversion is direct only for decoders that override `unsafeFromJsonAST`: the primitive types, `Option`, `Chunk`, `java.time` types and derived case classes and sealed traits. Other decoders, such as those for `List`, `Vector`, `Set` and `Map`, and hand-written decoders, encode the `Json` back to text and parse that text. [Decoding from the AST](../reference/json-decoder.md#decoding-from-the-ast) explains how to override it.

### Large Payloads

`Chunk[Byte]`, `Array[Byte]` and `String` inputs all hold the whole payload in memory. On the JVM, the streaming decoders (`JsonDecoder#decodeJsonStreamInput` for bytes and `JsonDecoder#decodeJsonStream` for characters) read the input incrementally instead, and the pipelines decode a stream of many values. See [Streaming API](../reference/streaming.md).

## Even More Performance

If ZIO JSON isn't fast enough for you, try [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala). ZIO JSON is fully integrated into ZIO, including streams and pipelines, while jsoniter-scala is library agnostic.

JSON is an inefficient transport format, and everybody would benefit from a port of this library to msgpack or protobuf. For legacy services, a port supporting XML is also possible.
