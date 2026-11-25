# Tutorial: Compress and Archive Files

> Welcome! In this tutorial you will compress a text file with Gzip, then bundle several files into a ZIP archive and read the archive back. Each step builds on the one before. By the end, you will have a small program that does all of it.

You need Scala and sbt, and a project with ZIO Streams. The steps use the `gzip` and `zip` modules, which need only ZIO Streams and the JDK.

## Step 1: Add the Dependencies

Add the two modules to your `build.sbt` file:

```sbt
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-streams-compress-gzip" % "3.0.1",
  "dev.zio" %% "zio-streams-compress-zip" % "3.0.1",
)
```

The `gzip` module provides `GzipCompressor` and `GzipDecompressor`. The `zip` module provides `ZipArchiver` and `ZipUnarchiver`. Both modules depend on `core`, which is pulled in automatically.

## Step 2: Compress a File with Gzip

We begin with one file. Create a file named `notes.txt` in your project directory, with any text in it. Then compress it into `notes.txt.gz` with this program:

```scala
import zio._
import zio.compress._
import zio.stream._

val compressNotes: ZIO[Any, Throwable, Long] =
  ZStream
    .fromFileName("notes.txt")
    .via(GzipCompressor.compress)
    .run(ZSink.fromFileName("notes.txt.gz"))
```

`ZStream.fromFileName` reads the file in chunks. `GzipCompressor.compress` turns those chunks into compressed chunks, and `ZSink.fromFileName` writes them to the new file. Nothing is read until the program runs the pipeline with `run`.

## Step 3: Decompress the File

To decompress the file again, swap the compressor for a decompressor:

```scala
import zio._
import zio.compress._
import zio.stream._

val decompressNotes: ZIO[Any, Throwable, Long] =
  ZStream
    .fromFileName("notes.txt.gz")
    .via(GzipDecompressor.decompress)
    .run(ZSink.fromFileName("notes-copy.txt"))
```

The copy `notes-copy.txt` is identical to `notes.txt`. Gzip is a single-file format, so it does not record the file name. That is why the output name is chosen here.

## Step 4: Archive Several Files into a ZIP

A ZIP archive holds many entries, and each entry has a name and content. An entry is a pair of an `ArchiveEntry` and a stream of bytes. Build one entry per file, and let `ZipArchiver` write them:

```scala
import zio._
import zio.compress._
import zio.stream._

def fileEntry(path: String): (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]) =
  (ArchiveEntry[Option, Any](path), ZStream.fromFileName(path))

val archiveNotes: ZIO[Any, Throwable, Long] =
  ZStream
    .fromIterable(Seq("notes.txt", "notes-copy.txt"))
    .map(fileEntry)
    .via(ZipArchiver().archive)
    .run(ZSink.fromFileName("bundle.zip"))
```

The entries do not need their size, so `ArchiveEntry[Option, Any](path)` is enough. `ZipArchiver` deflates each entry by default.

## Step 5: List the Archive

To check what is inside `bundle.zip`, read its entries back with `ZipUnarchiver`. The `list` method fills in the size of each entry:

```scala
import zio._
import zio.compress._
import zio.stream._

val listBundle: ZIO[Any, Throwable, Chunk[String]] =
  ZStream
    .fromFileName("bundle.zip")
    .via(ZipUnarchiver().list)
    .map(entry => s"${entry.name}: ${entry.uncompressedSize.getOrElse(-1L)} bytes")
    .runCollect
```

When you need the content of each entry, use `unarchive` instead. Then read each entry's content before the next entry is emitted. The [Read Archives Safely](./how-to-read-archives-safely.md) guide explains why.

## Step 6: Put It All Together

Here is the complete program. It compresses `notes.txt`, bundles the files, and lists the archive:

```scala
import zio._
import zio.compress._
import zio.stream._

object CompressAndArchive extends ZIOAppDefault {

  private def fileEntry(path: String): (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]) =
    (ArchiveEntry[Option, Any](path), ZStream.fromFileName(path))

  override def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- ZStream
             .fromFileName("notes.txt")
             .via(GzipCompressor.compress)
             .run(ZSink.fromFileName("notes.txt.gz"))
      _ <- ZStream
             .fromIterable(Seq("notes.txt", "notes.txt.gz"))
             .map(fileEntry)
             .via(ZipArchiver().archive)
             .run(ZSink.fromFileName("bundle.zip"))
      entries <- ZStream
                   .fromFileName("bundle.zip")
                   .via(ZipUnarchiver().list)
                   .map(entry => s"${entry.name}: ${entry.uncompressedSize.getOrElse(-1L)} bytes")
                   .runCollect
      _ <- ZIO.foreachDiscard(entries)(line => Console.printLine(line))
    } yield ()
}
```

Run the program with `sbt run`. It prints one line per entry in `bundle.zip`.

## Where to Go Next

You now know how to compress a stream, decompress it, and archive several entries. The [Create and Read Archives](./how-to-create-and-read-archives.md) guide covers Tar archives, and the [Compress and Decompress](./how-to-compress-and-decompress.md) guide shows how to swap codecs without changing the rest of the program. For every option of every module, see the reference pages in the sidebar.
