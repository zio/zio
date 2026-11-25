# Brotli

> The `brotli` module decompresses the Brotli format. It is built on the official Brotli Java decoder, which is pure Java. This module does not compress. To compress Brotli, use the [Brotli4J](./reference-brotli4j.md) module, which compresses and decompresses.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli" % "3.0.1"
```

## BrotliDecompressor

`BrotliDecompressor` is a `Decompressor` that reads the Brotli format:

```scala
import zio.compress._
import zio.stream._

val decompressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromFileName("file.br").via(BrotliDecompressor.decompress)
```

To read data that was compressed with a custom dictionary, pass the same dictionary to `BrotliDecompressor.apply`:

```scala
import zio.compress._

def dictionaryDecompressor(dictionary: Array[Byte]): BrotliDecompressor =
  BrotliDecompressor(customDictionary = Some(dictionary))
```

## Options

| Option             | Type                       | Default                             |
|--------------------|----------------------------|-------------------------------------|
| `customDictionary` | `Option[Array[Byte]]`      | `None` (no dictionary)              |
| `chunkSize`        | `Int`                      | 4 KiB (`ZStream.DefaultChunkSize`)  |

The `chunkSize` is the largest chunk emitted by the output stream.

## Notes

- The decompressor's error type is `Throwable`. Input that is not valid Brotli fails the stream.
- This module cannot produce Brotli data. Use `Brotli4JCompressor` for that.
