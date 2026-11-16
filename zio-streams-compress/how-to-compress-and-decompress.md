# How to Compress and Decompress a Stream

> Every codec in this library is a `Compressor` or a `Decompressor`, so a stream can be compressed or decompressed by changing one value. This guide shows how to compress a file with any codec, decompress it again, and keep the codec choice in one place.

## Compress a File

A compressor is a pipeline. Read the file with `ZStream.fromFileName`, pipe it through the compressor, and write the result with `ZSink.fromFileName`. The function below takes any `Compressor`:

```scala
import zio._
import zio.compress._
import zio.stream._

def compressFile(input: String, output: String, compressor: Compressor): ZIO[Any, Throwable, Long] =
  ZStream
    .fromFileName(input)
    .via(compressor.compress)
    .run(ZSink.fromFileName(output))
```

`run` returns the number of bytes written to `output`. The function works the same for Gzip, Zstd, LZ4, and any other codec.

## Decompress a File

Decompression is the same pipeline with a `Decompressor`:

```scala
import zio._
import zio.compress._
import zio.stream._

def decompressFile(input: String, output: String, decompressor: Decompressor): ZIO[Any, Throwable, Long] =
  ZStream
    .fromFileName(input)
    .via(decompressor.decompress)
    .run(ZSink.fromFileName(output))
```

Use the decompressor that matches the compressor. Gzip data needs a Gzip decompressor, and Zstd data needs a Zstd decompressor.

## Choose a Codec in One Place

Keep the codec choice in a single value, so the rest of the program does not change when the choice does:

```scala
import zio.compress._

val compressorChoice: Compressor = GzipCompressor(deflateLevel = DeflateCompressionLevel(9))

val decompressorChoice: Decompressor = GzipDecompressor()
```

To switch to Zstd, replace the two values with `ZstdCompressor` and `ZstdDecompressor`. The functions above stay unchanged.

:::tip[Pick a codec by its trade-off]
Gzip is the most widely supported format. Zstd gives good compression at high speed. Brotli4J compresses text well at a high quality setting. LZ4 and Snappy favor speed over ratio. Bzip2 compresses well but uses a lot of memory. See the reference page of each codec for its options.
:::

## Round Trip a File

To check that a codec works for your data, compress a file, decompress the result, and compare the two:

```scala
import zio._
import zio.compress._
import zio.stream._

def gzipFile(input: String, output: String): ZIO[Any, Throwable, Long] =
  ZStream
    .fromFileName(input)
    .via(GzipCompressor.compress)
    .run(ZSink.fromFileName(output))

val roundTrip: ZIO[Any, Throwable, Boolean] =
  for {
    _ <- gzipFile("notes.txt", "notes.txt.gz")
    original <- ZStream.fromFileName("notes.txt").runCollect
    restored <- ZStream.fromFileName("notes.txt.gz").via(GzipDecompressor.decompress).runCollect
  } yield original == restored
```

Both sides are `Chunk[Byte]`, so `==` compares the bytes.

## Notes

- Compression is lazy. Nothing is read from the file until the pipeline runs.
- A codec may fail on invalid input. The error appears in the `Throwable` of the pipeline.
- To compress several files into one archive, see [Create and Read Archives](./how-to-create-and-read-archives.md).
