# Zstd

> The `zstd` module compresses and decompresses the Zstandard format. It wraps the `com.github.luben:zstd-jni` library, which binds the native Zstandard library.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-zstd" % "3.0.1"
```

## ZstdCompressor

`ZstdCompressor` is a `Compressor` that writes the Zstandard format. Use `ZstdCompressor.apply` to set its options, or `ZstdCompressor.compress` for the library defaults:

```scala
import zio.compress._
import zio.stream._

val compressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromIterable("Hello, ZIO!".getBytes("UTF-8")).via(ZstdCompressor.compress)
```

To choose a level and use several worker threads:

```scala
import zio.compress._

val tuned = ZstdCompressor(
  level = ZstdCompressionLevel(19),
  workers = Some(4),
)
```

`ZstdCompressionLevel(19)` returns an `Option`, which matches the type of the `level` option. A level that is out of range gives `None`, and the library default applies.

## ZstdDecompressor

`ZstdDecompressor` is a `Decompressor` that reads the Zstandard format:

```scala
import zio.compress._
import zio.stream._

val decompressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromFileName("file.zst").via(ZstdDecompressor.decompress)
```

## Options

| Option             | Type                             | Default                                    |
|--------------------|----------------------------------|--------------------------------------------|
| `level`            | `Option[ZstdCompressionLevel]`   | `None` (the library default, level 3)      |
| `workers`          | `Option[Int]`                    | `None` (the library default)               |
| `customDictionary` | `Option[Array[Byte]]`            | `None` (no dictionary)                     |
| `chunkSize`        | `Int` (decompressor only)        | 4 KiB (`ZStream.DefaultChunkSize`)         |

`ZstdCompressionLevel` ranges from -131072 to 22. Negative levels trade compression ratio for speed. Named constants cover the common cases:

- `ZstdCompressionLevel.FastCompressionLevel` is -4.
- `ZstdCompressionLevel.DefaultCompressionLevel` is 3.
- `ZstdCompressionLevel.HighCompressionLevel` is 6.
- `ZstdCompressionLevel.VeryHighCompressionLevel` is 19.
- `ZstdCompressionLevel.UltraCompressionLevel` is 22. Levels 20 to 22 need a lot of memory.

`workers` is the number of threads for compression. Use `Some(0)` to detect the number of CPU cores. The library limits the number of workers it runs.

## Notes

- The decompressor accepts no dictionary. Data compressed with a `customDictionary` cannot be decompressed by this module.
- The compressor's error type is `Throwable`, because the native library can fail.
- Input that is not valid Zstandard fails the stream with a `Throwable`.
