# Compression and archives with zio-streams

> [ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and archive formats with [ZIO Streams](https://zio.dev). Every codec and archive format is a pipeline, so it fits into a stream with `via` like any other operator.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-streams-compress/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-streams-compress-docs_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-streams-compress-docs_2.13) [![Sonatype Snapshots](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fzio%2Fzio-streams-compress-docs_2.13%2Fmaven-metadata.xml&label=Sonatype%20Snapshot)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-streams-compress-docs_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-streams-compress-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-streams-compress-docs_2.13) [![ZIO Streams Compress docs](https://img.shields.io/github/stars/zio/zio-streams-compress?style=social)](https://github.com/zio/zio-streams-compress) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Installation

To use the library, add one or more of the following lines to your `build.sbt` file:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli4j" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-bzip2" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-lz4" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-snappy" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "3.0.0"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zstd" % "3.0.0"
```

Each module depends on `core`, which is pulled in automatically. Add only the modules you use.

_As of 2.1.3, zio-streams-compress requires zio 2.1.25 or later._

The library is published for Scala 3, 2.13, and 2.12, and runs on the JVM. Scala.js and Scala Native are not supported. PRs for them are welcome.

## Choosing a Codec

The table lists each codec with its module. Compression and decompression are both available unless the table says otherwise.

| Format      | Module       | Compress | Decompress | Notes                                             |
|-------------|--------------|----------|------------|---------------------------------------------------|
| Gzip        | `gzip`       | Yes      | Yes        | Widely supported. Built on the JDK.               |
| Zstd        | `zstd`       | Yes      | Yes        | Good ratio at high speed. Dictionary on write only. |
| Brotli      | `brotli`     | No       | Yes        | Decompression only, pure Java.                    |
| Brotli      | `brotli4j`   | Yes      | Yes        | Compresses text well. Loads a native library.     |
| Bzip2       | `bzip2`      | Yes      | Yes        | Good ratio. Uses a lot of memory.                 |
| LZ4         | `lz4`        | Yes      | Yes        | Very fast. Favors speed over ratio.               |
| Snappy      | `snappy`     | Yes      | Yes        | Fast. Three write formats.                        |
| ZIP         | `zip`        | Yes      | Yes        | Archive format. Entries may have unknown size.    |
| ZIP         | `zip4j`      | Yes      | Yes        | Archive format. Reads password-protected archives. |
| Tar         | `tar`        | Yes      | Yes        | Archive format. No compression of its own.        |

For a bundle of files, pick an archive format (ZIP or Tar). For a single stream of bytes, pick a codec. A `.tar.gz` file is a Tar archive wrapped in Gzip.

## Core Concepts

The library has two families of pipeline. A compressor turns bytes into compressed bytes, and a decompressor does the reverse. An archiver turns a stream of named entries into the bytes of an archive, and an unarchiver does the reverse. Every codec in the table is a compressor, a decompressor, or both. Every archive format is an archiver, an unarchiver, or both.

The [Core Interfaces](./reference-core.md) reference describes these types in full. The [Tutorial](./tutorial-compress-and-archive.md) builds a small program that uses them.

## Example

The example below compresses a file with Gzip, lists the entries of a Tar archive compressed with Gzip, and creates a ZIP archive:

```scala
import zio._
import zio.compress._
import zio.stream._

object ExampleApp extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Any] =
    for {
      // Compress a file with Gzip
      _ <- ZStream
             .fromFileName("file")
             .via(GzipCompressor.compress)
             .run(ZSink.fromFileName("file.gz"))

      // List all items in a gzipped tar archive
      _ <- ZStream
             .fromFileName("file.tgz")
             .via(GzipDecompressor.decompress)
             .via(TarUnarchiver().list)
             .mapZIO { entry =>
               Console.printLine(s"${entry.name} ${entry.uncompressedSize.getOrElse(-1L)}")
             }
             .runDrain

      // Create a ZIP archive with one entry
      _ <- ZStream(entry("file1.txt", "Hello world!".getBytes("UTF-8")))
             .via(ZipArchiver().archive)
             .run(ZSink.fromFileName("file.zip"))
    } yield ()

  private def entry(
    name: String,
    content: Array[Byte],
  ): (ArchiveEntry[Option, Any], ZStream[Any, Throwable, Byte]) =
    (ArchiveEntry[Option, Any](name), ZStream.fromIterable(content))
}
```

The ZIP archive in the example is not encrypted. Passing a password to `Zip4JArchiver` does not encrypt yet, so the example does not use it. See the [Zip4J](./reference-zip4j.md) reference for the current state.

## Unarchiving

As of zio-streams-compress 2.0, all unarchivers require the consumer to fully read the entry's contents before the next archive entry is emitted.

⚠️ Not reading the entry's content causes the unarchive pipeline to lock up and halt your program.

In zio-streams-compress 1.x, consuming the next archive entry, even by accident for example by using buffering or rechunking, corrupts the content stream of all archive entries. With the new requirement the unarchiver can continue to operate in a streaming fashion without corrupting content.

Most of the time the new requirement should not be an issue. For example, you can concatenate `unarchive` and `archive` pipelines without problems.

Two cases need care:

- You only need the metadata of the entries. Use the unarchiver's `list` method, which reads each entry's content behind the scenes.
- You need buffering, aggregation, rechunking or parallel processing to improve throughput. Read each entry's content in the step that handles the entry, then do the buffering, aggregation or parallel work. The [Read Archives Safely](./how-to-read-archives-safely.md) guide shows how, and how to limit the size of each entry.

## Where to Go Next

- [Tutorial: Compress and Archive Files](./tutorial-compress-and-archive.md) walks through the basics in order.
- [How to Compress and Decompress a Stream](./how-to-compress-and-decompress.md) shows how to switch codecs.
- [How to Create and Read Archives](./how-to-create-and-read-archives.md) covers ZIP and Tar.
- [How to Read a Password-Protected ZIP](./how-to-read-password-protected-zip.md) reads Zip4J archives.
- The reference pages describe every option and default of each module, from [Core Interfaces](./reference-core.md) to [Tar](./reference-tar.md).
