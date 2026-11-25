# LZ4

> The `lz4` module compresses and decompresses the LZ4 frame format. It is built on the official LZ4 Java library.

Add the module to your `build.sbt`:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-lz4" % "3.0.1"
```

## Lz4Compressor

`Lz4Compressor` is a `Compressor` that writes the LZ4 frame format. Use `Lz4Compressor.apply` to choose the block size, or `Lz4Compressor.compress` for the default block size:

```scala
import zio.compress._
import zio.stream._

val compressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromIterable("Hello, ZIO!".getBytes("UTF-8")).via(Lz4Compressor.compress)
```

To write blocks of 4 MiB:

```scala
import zio.compress._

val largeBlocks = Lz4Compressor(Lz4CompressorBlockSize.BlockSize4MiB)
```

## Lz4Decompressor

`Lz4Decompressor` is a `Decompressor` that reads the LZ4 frame format:

```scala
import zio.compress._
import zio.stream._

val decompressed: ZStream[Any, Throwable, Byte] =
  ZStream.fromFileName("file.lz4").via(Lz4Decompressor.decompress)
```

## Options

| Option      | Type                          | Default                             |
|-------------|-------------------------------|-------------------------------------|
| `blockSize` | `Lz4CompressorBlockSize`      | 256 KiB                             |
| `chunkSize` | `Int` (decompressor only)     | 4 KiB (`ZStream.DefaultChunkSize`)  |

`Lz4CompressorBlockSize` has four values: `BlockSize64KiB`, `BlockSize256KiB`, `BlockSize1MiB`, and `BlockSize4MiB`. A larger block size compresses better and uses more memory. To convert the numeric indicator of the LZ4 format (4 to 7), use `Lz4CompressorBlockSize.fromLz4BlockSizeIndicator`. It returns an `Option`, because other values are not valid.

## Notes

- Both pipelines fail with a `Throwable` when the input is not a valid LZ4 frame.
- The LZ4 frame format has no entry names or metadata. To compress several files, use a tar archive inside the LZ4 stream.
