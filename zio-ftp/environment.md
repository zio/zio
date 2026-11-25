# Environment

> ZIO FTP keeps its connection in the ZIO environment, so each operation states in its type which connection it needs. Nothing connects until you provide a layer, and the layer closes the connection when its scope ends.

## Three Environment Types

Plain FTP and FTPS operations require `Ftp`, SFTP operations require `SFtp`, and operations on the local test double require `StubFtp`. All three aliases live in the `zio.ftp` package object and are the same trait with a different underlying client:

```scala
package object ftp {
  type Ftp     = FtpAccessors[UnsecureFtp.Client] // Apache Commons Net FTPClient
  type SFtp    = FtpAccessors[SecureFtp.Client]   // sshj SFTPClient
  type StubFtp = FtpAccessors[Unit]               // no client: a local directory
}
```

The `FtpAccessors` trait is the common contract. Its methods need nothing from the environment except the environment of the source stream passed to `FtpAccessors#upload`, because the service is the object they belong to. The `Ftp`, `SFtp` and `StubFtp` objects take that service from the environment and forward the call to it:

```scala
object Ftp {
  def ls(path: String): ZStream[Ftp, IOException, FtpResource]
}
```

Because the three aliases are distinct types, one program can require two of them at once, for example to copy files from an FTP server to an SFTP server. The [Ftp API](reference/ftp-accessors.md) reference lists every accessor.

## Providing the Environment

Use `unsecure` for FTP and FTPS, `secure` for SFTP, and `stub` for the test double. The first two return a layer that connects when it is built, fails with a `ConnectionError` if the connection or login fails, and disconnects when its scope closes:

```scala
package object ftp {
  def unsecure(settings: UnsecureFtpSettings): ZLayer[Any, ConnectionError, Ftp]
  def secure(settings: SecureFtpSettings): ZLayer[Any, ConnectionError, SFtp]
  def stub(path: zio.nio.file.Path): Layer[Any, StubFtp]
}
```

To remove `Ftp` from an effect's requirements, pass the layer to `ZIO#provide`:

```scala
import zio._
import zio.ftp._

val settings = UnsecureFtpSettings("127.0.0.1", 21, PasswordCredentials("one", "1234"))

val listing = Ftp.ls("/").runCollect.provide(unsecure(settings))
```

For SFTP, the same pattern applies with `secure` and the `SFtp` accessors:

```scala
import zio.ftp._

val sftpSettings = SecureFtpSettings("127.0.0.1", 22, PasswordCredentials("one", "1234"))

val sftpListing = SFtp.ls("/").runCollect.provide(secure(sftpSettings))
```

Each `ZIO#provide` call builds the layer, so each of these two effects opens its own connection. In a real program, provide the layer once, at the edge, and write the rest of the program against `Ftp` or `SFtp`.

## Using Two Connections in One Program

A program that needs both an FTP and an SFTP connection lists both in its environment type and gets both layers. The following effect streams a file from an FTP server straight to an SFTP server, without storing it locally:

```scala
import zio._
import zio.ftp._
import java.io.IOException

def copyToSftp(path: String): ZIO[Ftp with SFtp, IOException, Unit] =
  SFtp.upload(path, Ftp.readFile(path))

val ftpSettings  = UnsecureFtpSettings("ftp.example.com", 21, PasswordCredentials("one", "1234"))
val sftpSettings = SecureFtpSettings("sftp.example.com", 22, PasswordCredentials("two", "5678"))

val copied = copyToSftp("/reports/2026-11.csv").provide(unsecure(ftpSettings), secure(sftpSettings))
```

The `Ftp.upload` and `SFtp.upload` accessors also require whatever environment their source stream needs, so here `SFtp.upload` requires `Ftp` because its source is an `Ftp.readFile` stream.

## Running Java Calls Safely

The FTP and SFTP Java clients only offer blocking methods. ZIO FTP runs every call to the Java client through `FtpAccessors#execute`, which the FTP and SFTP implementations define with `ZIO.attemptBlockingIO`. That moves the call onto ZIO's blocking thread pool, so the main pool keeps running other fibers, and turns any thrown `IOException` into a typed failure. In the plain FTP client, `UnsecureFtp#execute` is implemented as:

```scala
override def execute[T](f: Client => T): ZIO[Any, IOException, T] =
  attemptBlockingIO(f(unsafeClient))
```

You don't provide any extra layer for this. ZIO 2 has no default-services environment, so every operation requires only `Ftp`, `SFtp` or `StubFtp`, plus the source stream's environment for `FtpAccessors#upload`. If you call a Java client method that the library doesn't wrap, use `FtpAccessors#execute` so it runs on the blocking pool too; see [Call an Unwrapped Client Method](guides/call-an-unwrapped-client-method.md).

Each layer holds one connection, and the Commons Net FTP client is not safe to use from several fibers at once. Run operations on one `Ftp` service sequentially; [FTP, FTPS and SFTP](protocols.md#one-connection-one-conversation) explains why and what to do when you need parallel transfers.

## Example

The following program runs as a `ZIOAppDefault`. It provides `Ftp` once, at the edge of the program, and prints the root directory listing. It needs a running FTP server, so it is compile-only:

```scala
import zio._
import zio.ftp._
import zio.ftp.Ftp._

import java.io.IOException

object EnvironmentExample extends ZIOAppDefault {

  private val settings =
    UnsecureFtpSettings("127.0.0.1", 21, PasswordCredentials("one", "1234"))

  private val program: ZIO[Ftp, IOException, Unit] =
    for {
      files <- ls("/").runCollect
      _     <- ZIO.foreachDiscard(files)(file => Console.printLine(file.path))
    } yield ()

  override def run = program.provide(unsecure(settings))
}
```

## Further Reading

For the layers, the scoped constructors and the low-level clients, see [Connecting](reference/connecting.md). For the ZIO 1 to ZIO 2 changes to the environment and default services, see the [ZIO 2.x migration guide](https://zio.dev/guides/migrate/zio-2.x-migration-guide). For how ZIO runs synchronous code that waits on the operating system, see [Creating Effects](https://zio.dev/overview/creating-effects/#blocking-synchronous-code).
