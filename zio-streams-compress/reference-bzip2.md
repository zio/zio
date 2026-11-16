# Bzip2

> The `bzip2` module compresses and decompresses the Bzip2 format. It is built on the Apache Commons Compress library.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-bzip2" % "3.0.0"
```

## Bzip2Compressor

`Bzip2Compressor` is a `Compressor` that writes the Bzip2 format. Use `Bzip2Compressor.apply` to set the block size, or `Bzip2Compressor.compress` for the default block size:

```scala
import zio.compress._
import zio.stream._

val compressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromIterable("Hello, ZIO!".getBytes("UTF-8")).via(Bzip2Compressor.compress)
```

To write blocks of 100 KB instead of the default 900 KB:

```scala
import zio.compress._

val smallBlocks = Bzip2Compressor(blockSize = Bzip2BlockSize(1))
```

`Bzip2BlockSize(1)` returns an `Option`, which matches the `blockSize` parameter type.

Bzip2 uses a lot of memory. The memory needed grows with the block size. See the [Apache Commons Compress documentation](https://commons.apache.org/proper/commons-compress/javadocs/api-1.28.0/org/apache/commons/compress/compressors/bzip2/BZip2CompressorOutputStream.html) for the heap needed for each block size.

## Bzip2Decompressor

`Bzip2Decompressor` is a `Decompressor` that reads the Bzip2 format:

```scala
import zio.compress._
import zio.stream._

val decompressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromFileName("file.bz2").via(Bzip2Decompressor.decompress)
```

## Options

| Option      | Type                          | Default                             |
|-------------|-------------------------------|-------------------------------------|
| `blockSize` | `Option[Bzip2BlockSize]`      | `None` (900 KB)                     |
| `chunkSize` | `Int` (decompressor only)     | 4 KiB (`ZStream.DefaultChunkSize`)  |

`Bzip2BlockSize` ranges from 1 to 9, in units of 100 KB. Named constants `BlockSize100KB` to `BlockSize900KB` exist for each value. A larger block size gives a better compression ratio and needs more memory.

## Notes

- Both pipelines fail with a `Throwable` when the input is not valid Bzip2.
- Bzip2 compresses one stream at a time. It has no entry names or metadata. To compress several files, use a tar archive inside the Bzip2 stream.
