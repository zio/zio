# Download a File

> Read a remote file as a string, save it to disk without loading it into memory, and resume an interrupted download.

In this guide, we download files with ZIO FTP: read a small text file into a string, save a large file straight to disk, and resume a download that was interrupted. All three use `Ftp.readFile`, which streams a remote file's bytes.

## The Problem

With the Commons Net client, a download is an `FTPClient#retrieveFileStream` call whose result may be `null`, followed by a copy loop and an `FTPClient#completePendingCommand` call that is easy to forget. Without it, the next command on the connection fails:

```scala
import org.apache.commons.net.ftp.FTPClient
import java.io.FileOutputStream

def download(client: FTPClient, remote: String, local: String): Unit = {
  val in = client.retrieveFileStream(remote)
  if (in == null) sys.error(s"Cannot read $remote: ${client.getReplyString}")
  val out = new FileOutputStream(local)
  try in.transferTo(out)
  finally {
    in.close()
    out.close()
  }
  if (!client.completePendingCommand()) sys.error(s"Transfer of $remote did not complete")
}
```

ZIO FTP's `Ftp.readFile` does all of this inside a `ZStream`: it opens the transfer when the stream starts, completes the command when the stream ends, and fails the stream with `FileTransferIncompleteError` if the server doesn't confirm the transfer.

## Prerequisites

You need the `zio-ftp` dependency from [Getting Started](../getting-started.md) and a connection layer. The examples use the `Ftp` accessors; for SFTP, use `SFtp` and the `secure` layer. They share these imports:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.io.IOException
import java.nio.file.{ Files, Paths }
import java.nio.file.StandardOpenOption.{ APPEND, CREATE }
```

## Reading a Text File

For a file small enough to hold in memory, decode the byte stream and collect it:

```scala
val notes: ZIO[Ftp, IOException, String] =
  Ftp.readFile("/notes.txt").via(ZPipeline.utf8Decode).runCollect.map(_.mkString)
```

To process a text file line by line without holding it all, add `ZPipeline.splitLines` and consume the lines as they arrive:

```scala
val errorCount: ZIO[Ftp, IOException, Long] =
  Ftp
    .readFile("/logs/app.log")
    .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
    .filter(_.contains("ERROR"))
    .runCount
```

## Saving a File to Disk

To save a file, run the stream into `ZSink.fromPath`. The bytes go from the network to the disk in chunks, so the file's size doesn't matter. The sink fails with `Throwable`, so narrow the error with `ZIO#refineToOrDie`:

```scala
def saveTo(remote: String, local: String): ZIO[Ftp, IOException, Long] =
  Ftp.readFile(remote).run(ZSink.fromPath(Paths.get(local))).refineToOrDie[IOException]
```

The effect returns the number of bytes written. The optional `chunkSize` parameter of `Ftp.readFile`, 2048 bytes by default, sets the read buffer size; a larger value, such as `Ftp.readFile(remote, chunkSize = 64 * 1024)`, can speed up large transfers.

## Resuming an Interrupted Download

If a download breaks halfway, the bytes already on disk are still valid. The `fileOffset` parameter starts the transfer at a byte offset, so the following effect continues from the local file's current size and appends the rest:

```scala
def resume(remote: String, local: String): ZIO[Ftp, IOException, Long] =
  for {
    path   <- ZIO.succeed(Paths.get(local))
    offset <- ZIO.attemptBlockingIO(if (Files.exists(path)) Files.size(path) else 0L)
    added  <- Ftp
                .readFile(remote, fileOffset = offset)
                .run(ZSink.fromPath(path, options = Set(CREATE, APPEND)))
                .refineToOrDie[IOException]
  } yield offset + added
```

On FTP, the offset is sent with the `REST` command, which the server must support (most do). On SFTP, the read starts at the offset directly. Combine `resume` with a retry schedule to keep a large download going over a flaky network:

```scala
val stubborn: ZIO[Ftp, IOException, Long] =
  resume("/big/archive.tar", "downloads/archive.tar").retry(Schedule.recurs(5) && Schedule.spaced(10.seconds))
```

A broken connection usually stays broken, so in practice each retry should also reconnect. Wrap the effect in `ZIO.scoped` with `UnsecureFtp.connect`, as shown in [Connecting](../reference/connecting.md#scoped-connections), so every attempt opens a new connection.

## Handling a Missing File

If the remote file doesn't exist, the stream fails as soon as it starts: with `InvalidPathError` on FTP and with sshj's `SFTPException` on SFTP. Check first with `Ftp.stat` when a missing file is expected:

```scala
def readIfPresent(remote: String): ZIO[Ftp, IOException, Option[String]] =
  Ftp.stat(remote).flatMap {
    case Some(_) => Ftp.readFile(remote).via(ZPipeline.utf8Decode).runCollect.map(c => Some(c.mkString))
    case None    => ZIO.none
  }
```

## One Download at a Time

FTP sends every command over one control connection, and a download keeps it busy until the stream ends. Consume or close each `Ftp.readFile` stream before starting the next operation on the same connection, as all the examples above do by running the stream to completion. For parallel downloads, open one connection per download; see [FTP, FTPS and SFTP](../protocols.md#one-connection-one-conversation). The following function downloads several files one after another, which is safe on a single connection because `ZIO.foreach` runs the downloads sequentially:

```scala
def saveAll(names: List[String]): ZIO[Ftp, IOException, Long] =
  ZIO.foreach(names)(name => saveTo(s"/incoming/$name", s"downloads/$name")).map(_.sum)
```

## Going Further

To send files the other way, see [Upload a File](upload-a-file.md). For every parameter of `FtpAccessors#readFile` and its per-protocol behavior, see the [Ftp API](../reference/ftp-accessors.md#reading-files) reference.
