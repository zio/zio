# Core Interfaces

> The `core` module defines the interfaces that every codec and archive format in ZIO Streams Compress implements. Each codec module depends on `core`, so these types are always available once you add any other module from the [installation instructions](./index.md#installation).

Most users never implement these interfaces. They are useful for understanding what the codecs have in common and for writing code that accepts any codec.

## Compressor and Decompressor

A `Compressor` turns a raw byte stream into a compressed byte stream. A `Decompressor` does the reverse. Both expose a single pipeline:

```scala
trait Compressor {
  def compress: ZPipeline[Any, Throwable, Byte, Byte]
}

trait Decompressor {
  def decompress: ZPipeline[Any, Throwable, Byte, Byte]
}
```

Because every codec exposes the same shape, a function can accept any compressor:

```scala
import zio.compress._
import zio.stream._

def compressText(compressor: Compressor, text: String): ZStream[Any, Throwable, Byte] =
  ZStream.fromIterable(text.getBytes("UTF-8")).via(compressor.compress)
```

`Compressor.empty` and `Decompressor.empty` pass bytes through unchanged. They are useful as a default when the caller does not want compression:

```scala
import zio.compress._

val compressorOrNone: Compressor = Compressor.empty
```

## Archiver and Unarchiver

An `Archiver` takes a stream of archive entries and produces the bytes of an archive. Each entry is a pair of an `ArchiveEntry` and the content stream for that entry:

```scala
trait Archiver[-Size[A] <: Option[A]] {
  def archive: ZPipeline[
    Any,
    Throwable,
    (ArchiveEntry[Size, Any], ZStream[Any, Throwable, Byte]),
    Byte,
  ]
}
```

An `Unarchiver` does the reverse. It takes the bytes of an archive and produces the entries with their content:

```scala
trait Unarchiver[Size[A] <: Option[A], Underlying] {
  def unarchive: ZPipeline[
    Any,
    Throwable,
    Byte,
    (ArchiveEntry[Size, Underlying], ZStream[Any, Throwable, Byte]),
  ]

  def list: ZPipeline[Any, Throwable, Byte, ArchiveEntry[Some, Underlying]]
}
```

`Unarchiver#list` returns the entries with their uncompressed size filled in. It reads each entry's content to count the bytes, so it is slower than `unarchive` on large archives. See [Read Archives Safely](./how-to-read-archives-safely.md) for the rules that apply to `unarchive`.

## ArchiveEntry

`ArchiveEntry` describes one entry in an archive:

```scala
final case class ArchiveEntry[+Size[A] <: Option[A], +Underlying](
  name: String,
  uncompressedSize: Size[Long] = Option.empty[Long],
  isDirectory: Boolean = false,
  lastModified: Option[Instant] = None,
  lastAccess: Option[Instant] = None,
  creation: Option[Instant] = None,
)
```

The type parameter `Size` says whether the uncompressed size is known:

| Size    | Meaning                                  | Used by                                      |
|---------|------------------------------------------|----------------------------------------------|
| `Some`  | The uncompressed size is always present  | Writing with `Zip4JArchiver` and `TarArchiver` |
| `None`  | The uncompressed size is never present   | No built-in module writes it                |
| `Option`| The uncompressed size may or may not be present | Writing with `ZipArchiver`; reading with every unarchiver |

Create an entry with its name, and optionally its size. The `with*` methods copy an entry with one field changed:

```scala
import zio.compress._
import java.time.Instant

val withoutSize: ArchiveEntry[Option, Any] = ArchiveEntry[Option, Any]("notes/readme.txt")

val withSize: ArchiveEntry[Some, Any] =
  ArchiveEntry[Option, Any]("notes/readme.txt").withKnownUncompressedSize(12L)

val dir: ArchiveEntry[Option, Any] =
  ArchiveEntry[Option, Any]("notes").withIsDirectory(true)

val timestamped: ArchiveEntry[Option, Any] =
  withoutSize.withLastModified(Some(Instant.parse("2024-01-01T00:00:00Z")))
```

When the entry comes from a reader, `ArchiveEntry#underlying` gives the library object it was built from. The type depends on the archive format, for example `ZipEntry` for `ZipUnarchiver`.

## Size Requirements on Write

Some archive formats must know the size of each entry before its content is written. The `Size` type parameter enforces this at compile time:

- `Zip4JArchiver` and `TarArchiver` require `ArchiveEntry[Some, _]`. Use `withKnownUncompressedSize` or `ArchiveEntry[Some, Any](name, Some(size))`.
- `ZipArchiver` accepts `ArchiveEntry[Option, _]`, so the size may be omitted.

The writers that check sizes compare the number of bytes actually written with the declared size. A mismatch kills the stream with an `IllegalStateException`, which ZIO reports as a defect rather than a typed error. The check is a pipeline you can also apply yourself: `Archiver.checkUncompressedSize`.

## ArchiveSingleFileCompressor and ArchiveSingleFileDecompressor

These two helpers turn an archiver or unarchiver into a `Compressor` or `Decompressor` for a stream that holds exactly one file. They are useful for formats such as a zipped single file where the caller wants a plain byte pipeline.

`ArchiveSingleFileCompressor.forName` wraps a file name, with the size when the archiver requires it:

```scala
import zio.compress._

val zipOneFile = ArchiveSingleFileCompressor.forName(ZipArchiver(), "data.csv")

val tarOneFile = ArchiveSingleFileCompressor.forName(TarArchiver(), "data.csv", 1024L)
```

`ArchiveSingleFileDecompressor` takes an unarchiver and emits the content of the first non-directory entry. It drops the name, so use it only when the archive is known to hold one file. This example wraps a ZIP unarchiver:

```scala
import zio.compress._

val unzipOneFile = ArchiveSingleFileDecompressor(ZipUnarchiver())
```

## Chunk Sizes and Defaults

The `Defaults` object holds the shared values for the modules:

- `Defaults.DefaultChunkSize` is 64 KiB. It is the default buffer size for the gzip compressor and decompressor, and the chunk size for `ZipUnarchiver`, `TarUnarchiver`, and `Zip4JUnarchiver`.
- `Defaults.DefaultChunkedQueueSize` is 2. It is the size of the internal queue between a pipeline and the Java stream it wraps.

Some codecs use ZIO's own default of 4 KiB (`ZStream.DefaultChunkSize`) for their decompressors. Each reference page states the default for its module.

## Compression Levels

`DeflateCompressionLevel` is shared by the gzip and zip modules. Its `apply` method returns an `Option`, because only levels 0 to 9 are valid:

```scala
import zio.compress._

val level: Option[DeflateCompressionLevel] = DeflateCompressionLevel(9)

val invalid: Option[DeflateCompressionLevel] = DeflateCompressionLevel(10)

val best: DeflateCompressionLevel = DeflateCompressionLevel.BestCompressionLevel
```

Named constants exist for each level, plus `NoCompressionLevel`, `FastestCompressionLevel`, `DefaultCompressionLevel`, and `BestCompressionLevel`.
