# Upload a File

> Upload strings, local files and generated data to an FTP or SFTP server, safely and without loading files into memory.

In this guide, we upload data to a file server with ZIO FTP: a short string, a local file of any size, and data generated on the fly. Then we make the upload safe for readers on the other side by writing to a temporary name and renaming it when the transfer is complete.

## The Problem

With the Java clients, an upload means opening an `InputStream`, checking a boolean return value, mapping failures by hand, and remembering to close everything, all on a thread that blocks:

```scala
import org.apache.commons.net.ftp.{ FTP, FTPClient }
import java.io.FileInputStream

val client = new FTPClient()
client.connect("ftp.example.com", 21)
client.login("one", "1234")
client.setFileType(FTP.BINARY_FILE_TYPE)
client.enterLocalPassiveMode()
val in = new FileInputStream("build/report.csv")
try {
  if (!client.storeFile("/reports/report.csv", in)) sys.error(client.getReplyString)
} finally {
  in.close()
  client.logout()
  client.disconnect()
}
```

Every upload repeats this, and none of it composes with the rest of a ZIO program. ZIO FTP's `Ftp.upload` takes a `ZStream` of bytes and returns a `ZIO` effect that fails with `IOException`, so the connection, the threading and the cleanup are handled for you.

## Prerequisites

You need the `zio-ftp` dependency from [Getting Started](../getting-started.md) and a connection layer. The examples use FTP and the `Ftp` accessors; for SFTP, replace `Ftp` with `SFtp` and provide the `secure` layer. All examples share these imports:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.io.IOException
import java.nio.file.Paths
```

## Uploading a String

`Ftp.upload` takes the remote path and a `ZStream[R, Throwable, Byte]`. For text, turn the string into bytes with `ZStream.fromChunk`:

```scala
val greeting: ZIO[Ftp, IOException, Unit] =
  Ftp.upload("/hello.txt", ZStream.fromChunk(Chunk.fromArray("Hello, world!".getBytes("UTF-8"))))
```

## Uploading a Local File

`ZStream.fromPath` reads a local file in chunks, so even a multi-gigabyte file is uploaded without being loaded into memory:

```scala
val report: ZIO[Ftp, IOException, Unit] =
  Ftp.upload("/reports/2026-11.csv", ZStream.fromPath(Paths.get("build/report.csv")))
```

If the local file can't be read, its `Throwable` becomes an `IOException` and the upload fails. If the server rejects the path, for example because `/reports` does not exist, FTP fails with `InvalidPathError`.

## Uploading Generated Data

The source can be any stream, so you can upload data while you produce it. The following effect writes a CSV file from a stream of rows without building the whole file first:

```scala
case class Sale(id: Long, amount: BigDecimal)

def salesCsv(sales: ZStream[Any, Throwable, Sale]): ZStream[Any, Throwable, Byte] =
  (ZStream.succeed("id,amount") ++ sales.map(s => s"${s.id},${s.amount}"))
    .intersperse("\n")
    .via(ZPipeline.utf8Encode)

val salesExport: ZIO[Ftp, IOException, Unit] =
  Ftp.upload("/exports/sales.csv", salesCsv(ZStream(Sale(1, 9.99), Sale(2, 25.00))))
```

When the source stream needs an environment, such as a database connection, that environment is added to the upload's requirements: `Ftp.upload` returns `ZIO[R with Ftp, IOException, Unit]`.

## Creating the Target Directory

The parent directory must exist before the upload. On FTP, `Ftp.mkdir` creates one level at a time, so create each missing level first. The following helper works on FTP and SFTP alike:

```scala
def ensureDirectory(dir: String): ZIO[Ftp, IOException, Unit] = {
  val levels = dir.split('/').filter(_.nonEmpty).scanLeft("")(_ + "/" + _).drop(1)
  ZIO.foreachDiscard(levels.toList) { level =>
    Ftp.stat(level).flatMap(existing => Ftp.mkdir(level).when(existing.isEmpty))
  }
}

val reportInPlace: ZIO[Ftp, IOException, Unit] =
  ensureDirectory("/reports/2026") *>
    Ftp.upload("/reports/2026/11.csv", ZStream.fromPath(Paths.get("build/report.csv")))
```

## Making the Upload Atomic

A process on the server that picks up new files might read a file while it is still being written. To avoid that, upload to a temporary name and rename it once the transfer has finished. A failed upload leaves only the temporary file, which the cleanup removes:

```scala
def uploadAtomically[R](path: String, source: ZStream[R, Throwable, Byte]): ZIO[R with Ftp, IOException, Unit] = {
  val partial = s"$path.part"
  (Ftp.upload(partial, source) *> Ftp.rename(partial, path).refineToOrDie[IOException])
    .tapError(_ => Ftp.rm(partial).ignore)
}
```

`Ftp.rename` fails with `Exception` in its signature, so `ZIO#refineToOrDie` narrows it back to `IOException`; the failure is always an `IOException` at runtime. Many servers refuse to rename over an existing file, so remove the old file first if you replace one.

:::warning
On SFTP, `SFtp.upload` does not truncate an existing file. Uploading a shorter file over a longer one leaves the old file's trailing bytes in place. Upload to a new name and rename it, as above, or remove the old file with `SFtp.rm` first.
:::

## Running an Upload

Each of the effects above requires `Ftp`. Provide the connection once, at the edge of the program:

```scala
object UploadApp extends ZIOAppDefault {
  private val settings = UnsecureFtpSettings("127.0.0.1", 2121, PasswordCredentials("username", "userpass"))

  override def run =
    uploadAtomically("/reports/2026-11.csv", ZStream.fromPath(Paths.get("build/report.csv")))
      .provide(unsecure(settings))
}
```

## Going Further

To read files back, see [Download a File](download-a-file.md). For every parameter of `FtpAccessors#upload` and the per-protocol differences, see the [Ftp API](../reference/ftp-accessors.md#writing-files) reference.
