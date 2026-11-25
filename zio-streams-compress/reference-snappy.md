# Snappy

> The `snappy` module compresses and decompresses the Snappy format. It is built on the [snappy-java](https://github.com/xerial/snappy-java) library.

Snappy has several incompatible variants. Before you choose a format, read the [snappy-java compatibility notes](https://github.com/xerial/snappy-java/blob/master/README.md#compatibility-notes).

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-snappy" % "3.0.1"
```

## SnappyCompressor

`SnappyCompressor` is a `Compressor` that writes Snappy. It takes a write format. The default is the framed format, and `SnappyCompressor.compress` uses that default:

```scala
import zio.compress._
import zio.stream._

val compressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromIterable("Hello, ZIO!".getBytes("UTF-8")).via(SnappyCompressor.compress)
```

To write the basic format with 64 KiB blocks:

```scala
import zio.compress._

val basic = SnappyCompressor(SnappyWriteFormat.Basic(blockSize = 64 * 1024))
```

## SnappyDecompressor

`SnappyDecompressor` is a `Decompressor` that reads Snappy. It takes a read format. The default is the framed format:

```scala
import zio.compress._
import zio.stream._

val decompressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromFileName("file.sz").via(SnappyDecompressor.decompress)
```

## Write Formats

`SnappyWriteFormat` has three variants:

| Variant                         | Options and defaults                                                                  |
|---------------------------------|---------------------------------------------------------------------------------------|
| `SnappyWriteFormat.Framed`      | `blockSize` 64 KiB, must be 1 to 64 KiB. `minCompressionRatio` 0.85, must be in (0, 1].|
| `SnappyWriteFormat.Basic`       | `blockSize` 32 KiB, must be 1 KiB to 512 MiB.                                         |
| `SnappyWriteFormat.HadoopCompatible` | `blockSize` 32 KiB, must be 1 KiB to 512 MiB. Writes no file header.             |

`Framed` follows the [Snappy framing format](https://github.com/google/snappy/blob/master/framing_format.txt). The `minCompressionRatio` is the smallest ratio of compressed to raw size that must be reached before a block is written compressed. Blocks that do not reach it are stored uncompressed.

`Basic` writes the raw Snappy format, with no framing. `HadoopCompatible` writes the format of Hadoop and Spark, which puts the block size before each block.

An invalid option, such as a block size out of range, throws an `IllegalArgumentException` when you create the format.

## Read Formats

`SnappyReadFormat` has two variants:

| Variant                     | Options and defaults                                                                   |
|-----------------------------|----------------------------------------------------------------------------------------|
| `SnappyReadFormat.Framed`   | `verifyChecksums` `true`. When `true`, checksums in the input are verified.            |
| `SnappyReadFormat.Basic`    | `maxChunkSize` 512 MiB, must be 1 to 512 MiB. The largest number of bytes compressed together. |

Read the input with the format it was written with. The formats are not compatible with each other, so a mismatch produces errors or wrong output.

## Options

| Option      | Type                          | Default                             |
|-------------|-------------------------------|-------------------------------------|
| `format`    | `SnappyWriteFormat` (compressor) or `SnappyReadFormat` (decompressor) | `Framed()` |
| `chunkSize` | `Int` (decompressor only)     | 4 KiB (`ZStream.DefaultChunkSize`)  |
