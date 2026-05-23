# Compression and archives with zio-streams

> [ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and
archive formats with [ZIO Streams](https://zio.dev).

[ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and
archive formats with [ZIO Streams](https://zio.dev).

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-streams-compress/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-streams-compress-docs_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-streams-compress-docs_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-streams-compress-docs_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-streams-compress-docs_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-streams-compress-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-streams-compress-docs_2.13) [![ZIO Streams Compress docs](https://img.shields.io/github/stars/zio/zio-streams-compress?style=social)](https://github.com/zio/zio-streams-compress) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Installation

In order to use this library, we need to add one of the following line in our `build.sbt` file:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli4j" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-bzip2" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-lz4" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-snappy" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "2.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zstd" % "2.1.3"
```

_As of 2.2.0, zio-streams-compress requires zio 2.1.25 or later._

For Brotli you can choose between the 'brotli' and the 'brotli4j' version. The first is based on the official Java
library but only does decompression. The second is based on [Brotli4J](https://github.com/hyperxpro/Brotli4j) which does
compression and decompression.

For ZIP files you can choose between the 'zip' and the 'zip4j' version. The first allows you to tweak the compression
level, while the second allows you work with password-protected ZIP files.

Currently only jvm is supported. PRs for scala-js and scala-native are welcome.

### Example

```scala
// Example.sc
// Run with: scala-cli Example.sc
//> using dep dev.zio:zio-streams-compress-gzip:2.1.3
//> using dep dev.zio:zio-streams-compress-tar:2.1.3
//> using dep dev.zio:zio-streams-compress-zip4j:2.1.3

object ExampleApp extends ZIOAppDefault {
  override def run: ZIO[Any, Any, Any] =
    for {
      // Compress a file with GZIP
      _ <- ZStream
             .fromFileName("file")
             .via(GzipCompressor.compress)
             .run(ZSink.fromFileName("file.gz"))

      // List all items in a gzip tar archive:
      _ <- ZStream
             .fromFileName("file.tgz")
             .via(GzipDecompressor.decompress)
             .via(TarUnarchiver.unarchive)
             .mapZIO { case (archiveEntry, contentStream) =>
               for {
                 content <- contentStream.runCollect
                 _ <- Console.printLine(s"${archiveEntry.name} ${content.length}")
               } yield ()
             }
             .runDrain

      // Create an encrypted ZIP archive
      _ <- ZStream(archiveEntry("file1.txt", "Hello world!".getBytes(UTF_8)))
             .via(Zip4JArchiver(password = Some("it is a secret")).archive)
             .run(ZSink.fromFileName("file.zip"))
    } yield ()

  private def archiveEntry(
    name: String,
    content: Array[Byte],
  ): (ArchiveEntry[Some, Any], ZStream[Any, Throwable, Byte]) =
    (ArchiveEntry(name, Some(content.length.toLong)), ZStream.fromIterable(content))

}
```

## Unarchiving

As of zio-streams-compress 2.0, all unarchivers require the consumer to fully read the entry's contents before the next
archive entry is emitted.

⚠️ Not reading the entry's content, causes the unarchive pipeline to lock up and halt your program.

In zio-streams-compress 1.x, consuming the next archive entry, even by accident for example by using buffering or
rechunking, corrupts the content stream of all archive entries. With the new requirement the unarchiver can continue to
operate in a streaming fashion without corrupting content.

Most of the time the new requirement should not be an issue. For example, you can concatenate 'unarchive' and 'archive'
pipelines without problems.

Here are two cases where the requirement can be issue:

1. You are only interested in the metadata of the archive entries. In this case you can use the unarchiver's `list`
   method which drains the entry's contents behind the scenes.

2. Buffering, aggregation or rechunking is needed to improve throughput.
   In this case you can first slurp the content in memory, and then do the buffering/aggregation/rechunking.
   As the whole entry will be pulled into memory, checking the size is prudent. Be aware that the reported size might
   not be available, or may even be maliciously incorrect! Here is an example that loads at most `maxEntrySize` bytes
   per entry:

```scala
ZStream
  .fromFileName("file.zip")
  .via(ZipUnarchiver.unarchive)
  .mapZIO { case (archiveEntry, contentStream) =>
    if (entry.uncompressedSize.exists(_ > maxEntrySize)) ZIO.fail("archive entry too large")
    else contentStream.take(maxEntrySize + 1).runCollect.flatMap { content =>
      if (content.size > maxEntrySize) ZIO.fail("archive entry too large")
      else ZIO.succss((archiveEntry, content))
    }
  }
  .rechunk(10) // here it is safe to rechunk
```
