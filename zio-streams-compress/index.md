# Compression and archives with zio-streams

> [ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and
archive formats with [ZIO Streams](https://zio.dev).

[ZIO Streams Compress](https://github.com/zio/zio-streams-compress) integrates several compression algorithms and
archive formats with [ZIO Streams](https://zio.dev).

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-streams-compress/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-streams-compress-docs_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-streams-compress-docs_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-streams-compress-docs_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-streams-compress-docs_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-streams-compress-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-streams-compress-docs_2.13) [![ZIO Streams Compress docs](https://img.shields.io/github/stars/zio/zio-streams-compress?style=social)](https://github.com/zio/zio-streams-compress) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

## Installation

In order to use this library, we need to add one of the following line in our `build.sbt` file:

```sbt
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-brotli4j" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-bzip2" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-gzip" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-lz4" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-snappy" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-tar" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zip4j" % "1.1.3"
libraryDependencies += "dev.zio" %% "zio-streams-compress-zstd" % "1.1.3"
```

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
//> using dep dev.zio:zio-streams-compress-gzip:1.1.3
//> using dep dev.zio:zio-streams-compress-tar:1.1.3
//> using dep dev.zio:zio-streams-compress-zip4j:1.1.3

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

## Running the tests

```shell
SBT_OPTS="-Xmx4G -XX:+UseG1GC" sbt test
```
