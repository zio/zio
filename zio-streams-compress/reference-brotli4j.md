# Brotli4J

> The `brotli4j` module compresses and decompresses the Brotli format. It is built on the [Brotli4J](https://github.com/hyperxpro/Brotli4j) library, which loads a native Brotli library at runtime. The module loads that library the first time a pipeline runs.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli4j" % "3.0.1"
```

## Brotli4JCompressor

`Brotli4JCompressor` is a `Compressor` that writes the Brotli format. Use `Brotli4JCompressor.apply` to set its options, or `Brotli4JCompressor.compress` for the library defaults:

```scala
import zio.compress._
import zio.stream._

val compressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromIterable("Hello, ZIO!".getBytes("UTF-8")).via(Brotli4JCompressor.compress)
```

To choose a quality, a window size, and a mode:

```scala
import zio.compress._

val tuned = Brotli4JCompressor(
  quality = BrotliQuality(11),
  lgwin = BrotliLogWindow(22),
  mode = Some(BrotliMode.Text),
)
```

`BrotliQuality(11)` and `BrotliLogWindow(22)` return an `Option`, which matches the parameter types directly.

## Brotli4JDecompressor

`Brotli4JDecompressor` is a `Decompressor` that reads the Brotli format:

```scala
import zio.compress._
import zio.stream._

val decompressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromFileName("file.br").via(Brotli4JDecompressor.decompress)
```

## Options

| Option    | Type                              | Default                             |
|-----------|-----------------------------------|-------------------------------------|
| `quality` | `Option[BrotliQuality]`           | `None` (the library default)        |
| `lgwin`   | `Option[BrotliLogWindow]`         | `None` (the library default)        |
| `mode`    | `Option[BrotliMode]`              | `None` (the library default)        |
| `chunkSize` | `Int` (decompressor only)       | 4 KiB (`ZStream.DefaultChunkSize`)  |

`BrotliQuality` ranges from 0 to 11. Higher values compress more and run slower. Named constants `Quality0` to `Quality11` exist for each value.

`BrotliLogWindow` is the base-2 logarithm of the LZ window size. It ranges from 10 to 24. Larger windows find more matches in large inputs.

`BrotliMode` gives the encoder a hint about the input:

- `BrotliMode.Generic` makes no assumption about the input. This is the default behaviour of the library.
- `BrotliMode.Text` is for UTF-8 text.
- `BrotliMode.Font` is for the WOFF 2.0 font format.

## Notes

- Both the compressor and the decompressor fail with a `Throwable` when the native library cannot be loaded, or when the input is not valid Brotli.
- Use this module instead of [Brotli](./reference-brotli.md) when you need to compress.
