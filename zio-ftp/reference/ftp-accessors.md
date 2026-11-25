# Ftp API

> `FtpAccessors` is the interface every ZIO FTP connection implements, and `Ftp`, `SFtp` and `StubFtp` are its three flavors in the ZIO environment. This page documents every operation: what it takes, what it returns, and how it behaves on FTP, SFTP and the local test double.

## Definition

`FtpAccessors[+A]` is a trait parameterized by the type of the underlying Java client, `A`. Every operation returns a `ZIO` or a `ZStream` that fails with `IOException`. None needs anything from the environment except `FtpAccessors#upload`, which needs the environment `R` of its source stream, because the service itself holds the connection:

```scala
trait FtpAccessors[+A] {
  def execute[T](f: A => T): ZIO[Any, IOException, T]
  def stat(path: String): ZIO[Any, IOException, Option[FtpResource]]
  def readFile(path: String, chunkSize: Int = 2048, fileOffset: Long = 0): ZStream[Any, IOException, Byte]
  def rm(path: String): ZIO[Any, IOException, Unit]
  def rmdir(path: String): ZIO[Any, IOException, Unit]
  def mkdir(path: String): ZIO[Any, IOException, Unit]
  def ls(path: String): ZStream[Any, IOException, FtpResource]
  def lsDescendant(path: String): ZStream[Any, IOException, FtpResource]
  def upload[R](path: String, source: ZStream[R, Throwable, Byte]): ZIO[R, IOException, Unit]
  def rename(oldPath: String, newPath: String): ZIO[Any, IOException, Unit]
}
```

The `zio.ftp` package object fixes `A` for each protocol:

| Alias     | Definition                         | Underlying client                      | Layer                |
| --------- | ---------------------------------- | -------------------------------------- | -------------------- |
| `Ftp`     | `FtpAccessors[UnsecureFtp.Client]` | `org.apache.commons.net.ftp.FTPClient` | `unsecure(settings)` |
| `SFtp`    | `FtpAccessors[SecureFtp.Client]`   | `net.schmizz.sshj.sftp.SFTPClient`     | `secure(settings)`   |
| `StubFtp` | `FtpAccessors[Unit]`               | none                                   | `stub(path)`         |

The [Connecting](connecting.md) page documents the layers and the lower-level constructors.

## Accessor Objects

In application code you rarely hold an `FtpAccessors` value directly. Instead, the companion objects `Ftp`, `SFtp` and `StubFtp` offer the same operations as functions that take the service from the environment. For example, `Ftp.ls` has this signature:

```scala
object Ftp {
  def ls(path: String): ZStream[Ftp, IOException, FtpResource]
}
```

The three objects have the same ten functions with the same parameters, and differ only in the environment they require. Two details differ from the trait. `Ftp.upload` requires `R with Ftp` (and `SFtp.upload` and `StubFtp.upload` likewise add their service to `R`), so the source stream's environment is added to the connection. `Ftp.rename`, `SFtp.rename` and `StubFtp.rename` fail with `Exception` instead of `IOException`; the failure is always an `IOException` at runtime, but the type is wider, so `ZIO#refineToOrDie[IOException]` is needed if the rest of your program expects `IOException`.

Importing `zio.ftp.Ftp._` brings the functions into scope unqualified, which is how the examples on the other pages use them.

## Paths

Every operation takes paths as `String` values that the server interprets. Use absolute paths starting with `/`. On most servers, `/` is the user's home directory or chroot, not the server's file system root. ZIO FTP does not expand `~` or resolve relative paths; whatever you pass goes to the server unchanged (or, for `StubFtp`, is resolved inside the stub's root directory).

## Inspecting Files

To check whether a path exists and read its metadata, use `FtpAccessors#stat`. To list a directory, use `FtpAccessors#ls` or `FtpAccessors#lsDescendant`. All three produce [`FtpResource`](ftp-resource.md) values.

### stat

`FtpAccessors#stat` returns `Some(resource)` when the path exists and `None` when it doesn't. It fails only when the server cannot be asked:

```scala
import zio._
import zio.ftp._

val report =
  Ftp.stat("/reports/2026-11.csv").flatMap {
    case Some(file) => Console.printLine(s"${file.path}: ${file.size} bytes, modified ${file.lastModified}")
    case None       => Console.printLine("No report yet")
  }
```

On FTP, `FtpAccessors#stat` sends the `MLST` command, which the server must support (most modern servers do). On SFTP, `FtpAccessors#stat` returns a resource whose `isDirectory` is always `None`, because the SFTP implementation does not read the file type from the attributes. On FTP and `StubFtp`, it is `Some(true)` or `Some(false)`.

### ls

`FtpAccessors#ls` streams the entries of one directory, files and subdirectories alike, without descending into subdirectories:

```scala
import zio._
import zio.ftp._

val directories =
  Ftp.ls("/").filter(_.isDirectory.contains(true)).map(_.path).runCollect
```

Each entry's `path` is the directory path joined with the entry name, such as `/reports/2026-11.csv`. Listing a directory that does not exist produces an empty stream on SFTP and `StubFtp`; on FTP the result depends on the server, and the servers ZIO FTP is tested against also return an empty listing.

### lsDescendant

`FtpAccessors#lsDescendant` walks a directory tree and streams every file below it. Directories themselves are not emitted, only the files inside them:

```scala
import zio._
import zio.ftp._

val totalBytes =
  Ftp.lsDescendant("/reports").runFold(0L)(_ + _.size)
```

The walk is lazy and depth-first: each subdirectory is listed when the stream reaches it, so a large tree does not have to fit in memory. See [List a Directory Recursively](../guides/list-a-directory-recursively.md) for filtering and limiting the walk.

## Reading Files

`FtpAccessors#readFile` streams the bytes of a remote file. Its two optional parameters control the transfer.

`chunkSize` is the size of the buffer the stream reads at a time, 2048 bytes by default, and the stub ignores it. `fileOffset` is the number of bytes to skip at the start of the file, 0 by default: on FTP it sends the `REST` command, so the server starts the transfer at that offset; on SFTP the read starts at that offset; on the stub the bytes are dropped after reading.

To decode a text file:

```scala
import zio._
import zio.ftp._
import zio.stream._

val text: ZIO[Ftp, java.io.IOException, String] =
  Ftp.readFile("/notes.txt").via(ZPipeline.utf8Decode).runCollect.map(_.mkString)
```

If the file does not exist, the stream from `FtpAccessors#readFile` fails with `InvalidPathError` on FTP and `StubFtp`, and with sshj's `SFTPException` on SFTP. On FTP, the stream checks at the end that the server confirmed the transfer, and fails with `FileTransferIncompleteError` if it did not. Because FTP uses a single control connection, consume or close an `FtpAccessors#readFile` stream before running the next operation on the same connection. [Download a File](../guides/download-a-file.md) shows how to save a file locally and resume a download.

## Writing Files

`FtpAccessors#upload` writes a stream of bytes to a remote path. The source can come from anywhere: a string, a local file, or another server's `FtpAccessors#readFile`. Its environment `R` becomes part of the effect's environment. On FTP and SFTP, the source's `Throwable` failure is turned into an `IOException`; `StubFtp` keeps an `IOException` from the source as a failure and treats any other `Throwable` as a defect:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.nio.file.Paths

val local  = ZStream.fromPath(Paths.get("build/report.csv"))
val upload = Ftp.upload("/reports/2026-11.csv", local)
```

On FTP and `StubFtp`, an existing file at the path is replaced. On SFTP, the file is opened for writing without truncating it first, so uploading a shorter file over a longer one can leave the old file's trailing bytes in place; remove the old file with `SFtp.rm` first when that matters. The parent directory must exist on all three; FTP fails with `InvalidPathError` if the server rejects the path. See [Upload a File](../guides/upload-a-file.md).

## Managing Files and Directories

Four operations change the remote file system. Each returns `Unit` on success:

| Operation                  | What it does                         | FTP failure        | SFTP failure    | `StubFtp` failure |
| -------------------------- | ------------------------------------ | ------------------ | --------------- | ----------------- |
| `mkdir(path)`              | creates a directory                  | `InvalidPathError` | `SFTPException` | `IOException`     |
| `rm(path)`                 | deletes a file                       | `InvalidPathError` | `SFTPException` | `IOException`     |
| `rmdir(path)`              | deletes an empty directory           | `InvalidPathError` | `SFTPException` | `IOException`     |
| `rename(oldPath, newPath)` | renames or moves a file or directory | `InvalidPathError` | `SFTPException` | `IOException`     |

`FtpAccessors#mkdir` creates only the last path segment on FTP, so `Ftp.mkdir("/a/b/c")` fails unless `/a/b` exists. On SFTP and `StubFtp`, it creates missing parents too, and on SFTP it succeeds if the directory already exists. To create nested directories portably, create each level in turn:

```scala
import zio._
import zio.ftp._

def mkdirs(path: String) = {
  val levels = path.split('/').filter(_.nonEmpty).scanLeft("")(_ + "/" + _).drop(1)
  ZIO.foreachDiscard(levels) { dir =>
    Ftp.stat(dir).flatMap {
      case Some(_) => ZIO.unit
      case None    => Ftp.mkdir(dir)
    }
  }
}
```

`FtpAccessors#rmdir` fails if the directory is not empty on FTP and SFTP; on `StubFtp`, it is the same as `FtpAccessors#rm` and also fails on a non-empty directory. `FtpAccessors#rename` can move an entry to another directory when the server allows it.

## Running Unwrapped Client Calls

`FtpAccessors#execute` runs any function of the underlying Java client and turns a thrown `IOException` into a typed failure; on FTP and SFTP it runs on ZIO's blocking thread pool. Use it for commands the library does not wrap:

```scala
import zio._
import zio.ftp._

val workingDirectory: ZIO[Ftp, java.io.IOException, String] =
  Ftp.execute(_.printWorkingDirectory())
```

The function receives a Commons Net `FTPClient` for `Ftp`, an sshj `SFTPClient` for `SFtp`, and `()` for `StubFtp`. On FTP and SFTP, exceptions other than `IOException` are defects, not failures; on `StubFtp`, every exception is a defect. See [Call an Unwrapped Client Method](../guides/call-an-unwrapped-client-method.md).

## Related Types

The operations produce [`FtpResource`](ftp-resource.md) values and fail with the [errors](errors.md) listed on their own page. The connection settings are in [FTP and FTPS settings](unsecure-ftp-settings.md) and [SFTP settings](secure-ftp-settings.md), and the test double is described in [TestFtp](test-ftp.md). To see these operations working together, follow the [tutorial](../tutorials/back-up-a-folder.md).
