# Introduction to ZIO FTP

> [ZIO FTP](https://github.com/zio/zio-ftp) is a ZIO client for file servers that speak FTP, FTPS or SFTP. Each file operation is a ZIO effect, every directory listing and download is a `ZStream`, and the connection is a layer that opens when the program starts and closes when it ends.

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-ftp/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/maven-central/v/dev.zio/zio-ftp_2.13.svg?label=Sonatype%20Release)](https://central.sonatype.com/artifact/dev.zio/zio-ftp_2.13) [![Sonatype Snapshots](https://img.shields.io/badge/Sonatype%20Snapshot-published-blue)](https://central.sonatype.com/repository/maven-snapshots/dev/zio/zio-ftp_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-ftp_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-ftp_2.13) [![ZIO FTP](https://img.shields.io/github/stars/zio/zio-ftp?style=social)](https://github.com/zio/zio-ftp)

## Features

ZIO FTP wraps two Java clients: [Apache Commons Net](https://commons.apache.org/proper/commons-net/) for FTP and FTPS, and [sshj](https://github.com/hierynomus/sshj) for SFTP. It runs their blocking calls on ZIO's blocking thread pool and turns their errors into typed `IOException` failures. The library offers:

- One set of operations for all three protocols: `Ftp.ls`, `Ftp.lsDescendant`, `Ftp.stat`, `Ftp.readFile`, `Ftp.upload`, `Ftp.mkdir`, `Ftp.rm`, `Ftp.rmdir` and `Ftp.rename`, with the same accessors on `SFtp` and `StubFtp`.
- Streaming reads and writes, so large files never have to fit in memory.
- Password and SSH key authentication for SFTP, and explicit or implicit TLS for FTPS.
- Connection settings for passive mode, timeouts, TCP keepalive and proxies.
- A test double that works on a local directory, so tests run without a server.

## Installation

ZIO FTP is published for Scala 2.12, 2.13 and 3 on the JVM. To use it, add the following to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  // FTP, FTPS and SFTP clients as ZIO effects and streams (JVM only)
  "dev.zio" %% "zio-ftp" % "0.5.4"
)
```

## Example

The following program connects to an FTP server, uploads a small file and prints the names in the root directory:

```scala
import zio._
import zio.ftp._
import zio.stream._

object QuickStart extends ZIOAppDefault {

  private val settings =
    UnsecureFtpSettings("127.0.0.1", 21, PasswordCredentials("one", "1234"))

  private val program =
    for {
      _     <- Ftp.upload("/hello.txt", ZStream.fromChunk(Chunk.fromArray("Hello, ZIO FTP!".getBytes)))
      names <- Ftp.ls("/").map(_.path).runCollect
      _     <- ZIO.foreachDiscard(names)(Console.printLine(_))
    } yield ()

  override def run = program.provide(unsecure(settings))
}
```

For SFTP, the same program uses `SecureFtpSettings`, the `SFtp` accessors and the `secure` layer. The [Getting Started](getting-started.md) page runs a program like this one against a local server, step by step.

## Learn More

The rest of the documentation is organized as follows:

- [Getting Started](getting-started.md) installs the library and runs a first program against a local FTP server.
- [FTP, FTPS and SFTP](protocols.md) explains the three protocols and how their behavior differs in ZIO FTP.
- [Environment](environment.md) explains how the connection lives in the ZIO environment.
- The reference section documents every operation ([Ftp API](reference/ftp-accessors.md)), every layer and constructor ([Connecting](reference/connecting.md)), and every settings type ([FTP and FTPS settings](reference/unsecure-ftp-settings.md), [SFTP settings](reference/secure-ftp-settings.md)), plus [FtpResource](reference/ftp-resource.md), [Errors](reference/errors.md) and [TestFtp](reference/test-ftp.md).
- The [tutorial](tutorials/back-up-a-folder.md) builds a small backup tool from scratch.
- The how-to guides solve one task each: [uploading a file](guides/upload-a-file.md), [downloading a file](guides/download-a-file.md), [listing a directory recursively](guides/list-a-directory-recursively.md), [connecting over SFTP with a key](guides/connect-over-sftp-with-a-key.md), [using FTPS](guides/use-ftps.md), [keeping a connection alive](guides/keep-a-connection-alive.md), [testing without a server](guides/test-without-a-server.md) and [calling an unwrapped client method](guides/call-an-unwrapped-client-method.md).
