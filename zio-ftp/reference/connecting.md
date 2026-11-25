# Connecting

> ZIO FTP offers three ways to get a connection: a `ZLayer` for the environment, a scoped effect for code that manages its own connections, and an `UnsecureFtp.unsafe` or `SecureFtp.unsafe` wrapper around a Java client you created yourself. This page documents all three and what happens when a connection opens and closes.

## Layers

Most programs use a layer. The `zio.ftp` package object defines one constructor per connection type:

```scala
package object ftp {
  def unsecure(settings: UnsecureFtpSettings): ZLayer[Any, ConnectionError, Ftp]
  def secure(settings: SecureFtpSettings): ZLayer[Any, ConnectionError, SFtp]
  def stub(path: zio.nio.file.Path): Layer[Any, StubFtp]
}
```

`unsecure` connects to an FTP or FTPS server, `secure` connects to an SFTP server, and `stub` creates the local test double described in [TestFtp](test-ftp.md). The first two are `ZLayer.scoped` wrappers around the scoped constructors below, so the connection opens when the layer is built and closes when the layer's scope ends. Provide the layer once, where the program starts:

```scala
import zio._
import zio.ftp._

object Main extends ZIOAppDefault {
  private val settings = SecureFtpSettings("sftp.example.com", 22, PasswordCredentials("foo", "bar"))

  override def run =
    SFtp.ls("/").map(_.path).foreach(Console.printLine(_)).provide(secure(settings))
}
```

If the connection cannot be opened, building the layer fails with a [`ConnectionError`](errors.md) and the program does not start.

## Scoped Connections

`UnsecureFtp.connect` and `SecureFtp.connect` return the connection as a scoped effect. Use them when you need more than one connection of the same kind, or when a connection should live for less than the whole program:

```scala
object UnsecureFtp {
  type Client = org.apache.commons.net.ftp.FTPClient
  def connect(settings: UnsecureFtpSettings): ZIO[Scope, ConnectionError, FtpAccessors[Client]]
}

object SecureFtp {
  type Client = net.schmizz.sshj.sftp.SFTPClient
  def connect(settings: SecureFtpSettings): ZIO[Scope, ConnectionError, FtpAccessors[Client]]
}
```

[`FtpAccessors[UnsecureFtp.Client]`](ftp-accessors.md) is the same type as `Ftp`, so you call the trait's methods on the returned value directly. The following effect uploads several files in parallel, each over its own connection, with at most four connections open at a time:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.nio.file.Paths

val settings = UnsecureFtpSettings("ftp.example.com", 21, PasswordCredentials("one", "1234"))

def uploadAll(files: List[String]) =
  ZIO.foreachParDiscard(files) { name =>
    ZIO.scoped {
      UnsecureFtp.connect(settings).flatMap { ftp =>
        ftp.upload(s"/incoming/$name", ZStream.fromPath(Paths.get("outbox", name)))
      }
    }
  }.withParallelism(4)
```

`ZIO.scoped` closes each connection as soon as its upload finishes, whether it succeeded or failed.

## Wrapping an Existing Client

`UnsecureFtp.unsafe` and `SecureFtp.unsafe` wrap a Java client that is already connected and logged in:

```scala
object UnsecureFtp {
  def unsafe(c: Client): UnsecureFtp
}

object SecureFtp {
  def unsafe(c: Client): SecureFtp
}
```

You rarely need this: it is an advanced API for clients that need configuration the settings classes don't expose, such as a Commons Net `FTPClientConfig` for a server with an unusual listing format. Prefer the layers or the scoped constructors. With `UnsecureFtp.unsafe` or `SecureFtp.unsafe`, you own the client's lifecycle, so pair it with `ZIO.acquireRelease`:

```scala
import zio._
import zio.ftp._
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException

val customFtp: ZIO[Scope, IOException, Ftp] =
  ZIO
    .acquireRelease(ZIO.attemptBlockingIO {
      val client = new FTPClient()
      client.connect("ftp.example.com", 21)
      client.login("one", "1234")
      client.enterLocalPassiveMode()
      client
    })(client => ZIO.attemptBlockingIO(client.disconnect()).ignore)
    .map(UnsecureFtp.unsafe)
```

The returned `UnsecureFtp` and `SecureFtp` classes are the FTP and SFTP implementations of [`FtpAccessors`](ftp-accessors.md). They are sealed, so you can't extend them, but you can use them anywhere an `Ftp` or `SFtp` is expected.

## Connection Lifecycle

The connect and disconnect steps follow the settings. For FTP and FTPS, `UnsecureFtp.connect` does the following, in order:

1. Creates an `FTPClient`, or an `FTPSClient` in implicit or explicit mode when `sslParams` is set.
2. Sets the control encoding, or turns on UTF-8 auto-detection when `controlEncoding` is `None`.
3. Installs a socket factory that goes through `proxy`, if set, and turns on TCP keepalive with `keepalive`, if set.
4. Sets `defaultTimeout` as the control connection's read timeout, connects to `host` and `port`, and logs in with the credentials.
5. Switches to binary mode if `binary` is true and to passive mode if `passiveMode` is true.
6. Turns on remote verification of data connections if `remoteVerificationEnabled` is true.
7. For FTPS, sends `PBSZ` and `PROT` with the values from `sslParams`.
8. Sets `dataTimeout`.

If any step throws, or the server rejects the login, the effect fails with a `ConnectionError`. When the scope closes, the client sends `QUIT` and disconnects, ignoring errors.

For SFTP, `SecureFtp.connect` creates an sshj `SSHClient` with `sshConfig`, installs a proxy socket factory if `proxy` is set, sets up host key verification (accept any key when `strictHostKeyChecking` is false, otherwise load the `knownHosts` file if one is set), connects, authenticates with a password or a key, and opens an SFTP channel. Any failure becomes a `ConnectionError` whose message names the host and port. When the scope closes, the SFTP channel is closed and the SSH connection is disconnected, ignoring errors.

## Related Pages

The settings are documented in [FTP and FTPS settings](unsecure-ftp-settings.md) and [SFTP settings](secure-ftp-settings.md). For how layers fit into the environment, see [Environment](../environment.md).
