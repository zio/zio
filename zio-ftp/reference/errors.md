# Errors

> Every ZIO FTP operation fails with `java.io.IOException`. ZIO FTP defines three subclasses for the failures it detects itself, and passes through the Java clients' own `IOException` subclasses for the rest. This page lists them and shows how to handle them.

## Error Types

The three ZIO FTP error types are case classes that extend `IOException`, so their message is also available as `Throwable#getMessage`:

```scala
final case class ConnectionError(message: String, cause: Throwable) extends IOException(message, cause)

object ConnectionError {
  def apply(message: String): ConnectionError
}

final case class InvalidPathError(message: String) extends IOException(message)

final case class FileTransferIncompleteError(message: String) extends IOException(message)
```

Each type comes from a specific place:

| Error                         | Raised by                                                                             | When                                                                       |
| ----------------------------- | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| `ConnectionError`             | `unsecure`, `secure`, `UnsecureFtp.connect`, `SecureFtp.connect`                      | the connection, TLS or SSH handshake, host key check or login fails        |
| `InvalidPathError`            | FTP `rm`, `rmdir`, `mkdir`, `rename`, `upload` and `readFile`, and `StubFtp.readFile` | the server rejects a command for a path, or the file to read doesn't exist |
| `FileTransferIncompleteError` | `Ftp.readFile`                                                                        | the server doesn't confirm that a download completed                       |

`ConnectionError.apply(message)` creates an error whose cause is a plain `Throwable` with the same message; the connect functions use it when the FTP server rejects the login, and otherwise keep the original exception as the cause.

## Errors by Protocol

The SFTP implementation does not translate errors: when the server refuses an operation, sshj's `net.schmizz.sshj.sftp.SFTPException` (an `IOException`) reaches your code, with a message such as `No such file`. The stub fails with plain `IOException` values for `StubFtp.rm`, `StubFtp.rmdir`, `StubFtp.mkdir`, `StubFtp.rename` and `StubFtp.upload`. The full mapping, per operation:

| Operation                              | FTP and FTPS                  | SFTP            | StubFtp            |
| -------------------------------------- | ----------------------------- | --------------- | ------------------ |
| `FtpAccessors#readFile` (missing file) | `InvalidPathError`            | `SFTPException` | `InvalidPathError` |
| `FtpAccessors#readFile` (cut short)    | `FileTransferIncompleteError` | `IOException`   | not applicable     |
| `upload`                               | `InvalidPathError`            | `SFTPException` | `IOException`      |
| `mkdir`, `rm`, `rmdir`                 | `InvalidPathError`            | `SFTPException` | `IOException`      |
| `rename`                               | `InvalidPathError`            | `SFTPException` | `IOException`      |
| `stat`, `ls`, `lsDescendant`           | the client's `IOException`    | `SFTPException` | `IOException`      |

On every connection, a network failure in the middle of an operation surfaces as the Java client's `IOException`, such as `java.net.SocketTimeoutException` or Commons Net's `FTPConnectionClosedException`. The FTP messages for `InvalidPathError` name the operation and the path, for example `Path is invalid. Cannot delete file : /reports/old.csv`.

## Handling Errors

Since every failure is an `IOException`, handle the cases you care about and let the rest propagate. The following effect treats a missing file as an empty one, on FTP and SFTP alike:

```scala
import zio._
import zio.ftp._
import zio.stream._
import net.schmizz.sshj.sftp.{ Response, SFTPException }

def readOrEmpty(path: String): ZStream[SFtp, java.io.IOException, Byte] =
  SFtp.readFile(path).catchSome {
    case e: SFTPException if e.getStatusCode == Response.StatusCode.NO_SUCH_FILE => ZStream.empty
  }

def readOrEmptyFtp(path: String): ZStream[Ftp, java.io.IOException, Byte] =
  Ftp.readFile(path).catchSome { case _: InvalidPathError => ZStream.empty }
```

Building a layer fails only with `ConnectionError`, so `ZLayer#retry` retries connection problems only. It rebuilds the layer, so each attempt opens a new connection:

```scala
import zio._
import zio.ftp._

val settings = UnsecureFtpSettings("ftp.example.com", 21, PasswordCredentials("one", "1234"))

val resilientFtp: ZLayer[Any, ConnectionError, Ftp] =
  unsecure(settings).retry(Schedule.exponential(1.second) && Schedule.recurs(5))
```

## Related Pages

The operations that raise these errors are documented in the [Ftp API](ftp-accessors.md), and the connection steps that raise `ConnectionError` in [Connecting](connecting.md#connection-lifecycle).
