# How to Create and Read Archives

> An archive bundles several files into one stream of bytes. This guide shows how to build a ZIP or Tar archive from several entries, write it to a file, and read the entries back.

## Choose a Format

Pick the format that your consumers can open:

- Use ZIP with `ZipArchiver` when the archive is for Windows users or for tools that expect a ZIP file. ZIP entries can have unknown sizes when written, so `ZipArchiver` accepts any entry.
- Use Tar with `TarArchiver` when the archive is for Unix tooling. Tar entries must have a known size, and a tarball is usually compressed with Gzip afterwards.

## Build the Entries

Each entry is a pair of an `ArchiveEntry` and the stream of its content. Keep the entries in a stream, so that each file's content is read lazily:

```scala
import zio.compress._
import zio.stream._

val notes = "Meeting at 10:00".getBytes("UTF-8")
val todo = "Buy milk".getBytes("UTF-8")

val entries: ZStream[Any, Nothing, (ArchiveEntry[Option, Any], ZStream[Any, Nothing, Byte])] =
  ZStream.fromIterable(
    Seq(
      ArchiveEntry[Option, Any]("docs", isDirectory = true) -> ZStream.empty,
      ArchiveEntry[Option, Any]("docs/notes.txt") -> ZStream.fromIterable(notes),
      ArchiveEntry[Option, Any]("docs/todo.txt") -> ZStream.fromIterable(todo),
    )
  )
```

The first entry is a directory, which is set with `isDirectory = true`. The writer adds the trailing `/` to the name. A name that ends in `/` without that flag is written as a file, so use the flag for directories.

## Write a ZIP Archive

Pipe the entries through `ZipArchiver` and write the result to a file:

```scala
import zio._
import zio.compress._
import zio.stream._

val notes = "Meeting at 10:00".getBytes("UTF-8")

val writeZip: ZIO[Any, Throwable, Long] =
  ZStream(
    ArchiveEntry[Option, Any]("docs/notes.txt") -> ZStream.fromIterable(notes)
  ).via(ZipArchiver().archive)
    .run(ZSink.fromFileName("docs.zip"))
```

`ZipArchiver().archive` writes deflated entries. To set the compression level, pass one to the constructor, for example `ZipArchiver(level = DeflateCompressionLevel(9))`, and call `archive` on that instance.

## Write a Tar Archive

`TarArchiver` requires every entry to declare its size, so each entry uses `withKnownUncompressedSize`:

```scala
import zio._
import zio.compress._
import zio.stream._

val notes = "Meeting at 10:00".getBytes("UTF-8")
val todo = "Buy milk".getBytes("UTF-8")

val writeTarGz: ZIO[Any, Throwable, Long] =
  ZStream(
    ArchiveEntry[Option, Any]("docs/notes.txt").withKnownUncompressedSize(notes.length.toLong) -> ZStream.fromIterable(notes),
    ArchiveEntry[Option, Any]("docs/todo.txt").withKnownUncompressedSize(todo.length.toLong) -> ZStream.fromIterable(todo),
  ).via(TarArchiver().archive)
    .via(GzipCompressor.compress)
    .run(ZSink.fromFileName("docs.tar.gz"))
```

The `withKnownUncompressedSize` call is required here, because the tar writer only accepts `ArchiveEntry[Some, _]`. If the size does not match the bytes written, the stream dies with an `IllegalStateException`.

## Read a ZIP Archive

To read the entry names and sizes of a ZIP file, use `list`:

```scala
import zio._
import zio.compress._
import zio.stream._

val listZip: ZIO[Any, Throwable, Chunk[String]] =
  ZStream
    .fromFileName("docs.zip")
    .via(ZipUnarchiver().list)
    .map(entry => s"${entry.name} (${entry.uncompressedSize.getOrElse(-1L)} bytes)")
    .runCollect
```

To read the content as well, use `unarchive`, and read each entry before the next one is emitted. See [Read Archives Safely](./how-to-read-archives-safely.md) for the rules and for a size limit.

## Read a Tar Archive

Decompress the Gzip layer first, and then read the tar entries:

```scala
import zio._
import zio.compress._
import zio.stream._

val listTarGz: ZIO[Any, Throwable, Chunk[String]] =
  ZStream
    .fromFileName("docs.tar.gz")
    .via(GzipDecompressor.decompress)
    .via(TarUnarchiver().list)
    .map(_.name)
    .runCollect
```

## Notes

- `ZipArchiver` accepts entries with or without a size. `TarArchiver` and `Zip4JArchiver` require the size.
- For password-protected ZIP archives, use the Zip4J module. See [Zip4J](./reference-zip4j.md).
