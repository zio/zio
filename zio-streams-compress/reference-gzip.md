# Gzip

> The `gzip` module compresses and decompresses the Gzip format. It is built on the ZIO Streams compression support, which uses the JVM standard library.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "3.0.1"
```

## GzipCompressor

`GzipCompressor` is a `Compressor` that writes the Gzip format. Create one with `GzipCompressor.apply` to set its options, or use `GzipCompressor.compress` for the defaults:

```scala
import zio.compress._
import zio.stream._

val gzipped: ZStream[Any, Nothing, Byte] =
  ZStream.fromIterable("Hello, ZIO!".getBytes("UTF-8")).via(GzipCompressor.compress)
```

To choose the compression level and strategy:

```scala
import zio.compress._

val fastest = GzipCompressor(
  deflateLevel = DeflateCompressionLevel(1),
  deflateStrategy = Some(DeflateStrategy.HuffmanOnly),
)
```

`DeflateCompressionLevel(1)` returns an `Option`, which matches the parameter type directly. When the value is invalid, pass `None` instead of a level.

## GzipDecompressor

`GzipDecompressor` is a `Decompressor` that reads the Gzip format:

```scala
import zio.compress._
import zio.stream._

val unzipped: ZStream[Any, Throwable, Byte] =
  ZStream.fromFileName("file.gz").via(GzipDecompressor.decompress)
```

The decompressor accepts several concatenated Gzip members in one input and emits their content in order. This is the form that results from appending the output of several compressions to one file.

## Options

| Option           | Type                                    | Default                         |
|------------------|-----------------------------------------|---------------------------------|
| `deflateLevel`   | `Option[DeflateCompressionLevel]`       | `None` (ZIO's default, level 6) |
| `deflateStrategy`| `Option[DeflateStrategy]`               | `None` (default strategy)       |
| `bufferSize`     | `Int` (compressor and decompressor)     | 64 KiB                          |

`DeflateCompressionLevel` ranges from 0 (no compression) to 9 (best compression). See [Core Interfaces](./reference-core.md#compression-levels) for the named constants.

`DeflateStrategy` has two values:

- `DeflateStrategy.Filtered` suits data that a predictor filter has already transformed into many small values. It matches the zlib `Z_FILTERED` strategy.
- `DeflateStrategy.HuffmanOnly` skips string matching and only applies Huffman coding. It is fast, but compresses less.

The `bufferSize` is the largest chunk emitted by the output stream.

## Notes

- The compressor's error type is `Nothing`, so it cannot fail. Errors come from the upstream stream, not from compression.
- The decompressor fails with a `Throwable` when the input is not valid Gzip.
- The Gzip format is a single-file format. To compress several files into one archive, use a tar archive with Gzip around it. See [Tar](./reference-tar.md).
