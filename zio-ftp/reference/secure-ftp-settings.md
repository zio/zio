# SFTP Settings

> `SecureFtpSettings` describes a connection to an SFTP server, which is file transfer over SSH. This page documents its fields and constructors, and the credential types it accepts: passwords, SSH keys held in memory, and SSH key files.

## Definition

`SecureFtpSettings` is a case class with seven fields, none of which has a default value in the case class itself:

```scala
final case class SecureFtpSettings(
  host: String,
  port: Int,
  credentials: FtpCredentials,
  strictHostKeyChecking: Boolean,
  knownHosts: Option[String],
  proxy: Option[java.net.Proxy],
  sshConfig: net.schmizz.sshj.Config
)
```

The settings value is passed to the `secure` layer or to `SecureFtp.connect`, both documented in [Connecting](connecting.md).

## Construction

The companion has two constructors that fill in the security and SSH fields:

```scala
object SecureFtpSettings {
  def apply(host: String, port: Int, credentials: FtpCredentials): SecureFtpSettings
  def apply(host: String, port: Int, credentials: FtpCredentials, proxy: Option[java.net.Proxy]): SecureFtpSettings
}
```

Both set `strictHostKeyChecking = false`, `knownHosts = None` and `sshConfig = new net.schmizz.sshj.DefaultConfig()`. The four-argument form also sets the proxy:

```scala
import zio.ftp._
import java.net.{ InetSocketAddress, Proxy }

val direct = SecureFtpSettings("sftp.example.com", 22, PasswordCredentials("foo", "bar"))

val viaProxy = SecureFtpSettings(
  "sftp.example.com",
  22,
  PasswordCredentials("foo", "bar"),
  Some(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("proxy.internal", 1080)))
)
```

:::warning
With `strictHostKeyChecking = false`, the client accepts any host key, so it cannot detect a server that impersonates the real one. Turn strict checking on for any server reached over a network you don't control.
:::

## Fields

The fields cover where to connect, how to log in, and how to trust the server.

### Server and Proxy

The `host` field is the server's host name or IP address, and `port` is its SSH port, usually 22. The `proxy` field routes the SSH connection through a `java.net.Proxy`, such as a SOCKS proxy, when it is `Some`.

### Host Key Verification

The `strictHostKeyChecking` and `knownHosts` fields decide which server keys the client trusts.

When `strictHostKeyChecking` is false, the client accepts any host key and ignores `knownHosts`. When it is true, the client loads the OpenSSH `known_hosts` file at the path in `knownHosts` and accepts only keys listed there for that host. If `knownHosts` is `None`, no key is trusted and every connection fails with a `ConnectionError`.

To trust the keys in your own `known_hosts` file:

```scala
import zio.ftp._

val strict = SecureFtpSettings("sftp.example.com", 22, PasswordCredentials("foo", "bar")).copy(
  strictHostKeyChecking = true,
  knownHosts = Some(sys.props("user.home") + "/.ssh/known_hosts")
)
```

### SSH Configuration

The `sshConfig` field is the sshj `Config` used to create the SSH client. It controls the algorithms the client offers (ciphers, key exchange, MACs and signatures) and the keepalive provider. The default, `DefaultConfig`, is sshj's recommended set. Replace it only to work with servers that need algorithms sshj disables by default, or to restrict the client to a smaller set. For example, to offer only AES ciphers:

```scala
import zio.ftp._
import net.schmizz.sshj.DefaultConfig
import scala.jdk.CollectionConverters._

val config = new DefaultConfig()
config.setCipherFactories(config.getCipherFactories.asScala.filter(_.getName.startsWith("aes")).asJava)

val custom = SecureFtpSettings("sftp.example.com", 22, PasswordCredentials("foo", "bar")).copy(sshConfig = config)
```

The SFTP implementation has no timeout or TCP keepalive settings of its own; the connection uses sshj's defaults.

## Credentials

The `credentials` field takes any `FtpCredentials`, a sealed trait with two cases:

```scala
sealed trait FtpCredentials

final case class PasswordCredentials(username: String, password: String) extends FtpCredentials
final case class KeyCredentials(username: String, identity: SftpIdentity) extends FtpCredentials
```

`PasswordCredentials` logs in with SSH password authentication. `KeyCredentials` logs in with public key authentication, using the private key described by its `SftpIdentity`. [Connect over SFTP with a Key](../guides/connect-over-sftp-with-a-key.md) walks through key setup end to end.

## SftpIdentity

`SftpIdentity` is a sealed trait with two cases, one for a key held in a string and one for a key in a file. Both carry an optional passphrase that decrypts an encrypted private key:

```scala
sealed trait SftpIdentity {
  type KeyType
  val privateKey: KeyType
  val passphrase: Option[String]
}
```

`RawKeySftpIdentity` holds the private key as text, for example loaded from a secret store or an environment variable. Its optional `publicKey` is the matching public key text, which is only needed for key formats that don't embed it:

```scala
final case class RawKeySftpIdentity(
  privateKey: String,
  passphrase: Option[String] = None,
  publicKey: Option[String] = None
) extends SftpIdentity

object RawKeySftpIdentity {
  def apply(privateKey: String): RawKeySftpIdentity
  def apply(privateKey: String, passphrase: String): RawKeySftpIdentity
}
```

`KeyFileSftpIdentity` points at a private key file on the local disk:

```scala
final case class KeyFileSftpIdentity(privateKey: java.nio.file.Path, passphrase: Option[String] = None)
  extends SftpIdentity

object KeyFileSftpIdentity {
  def apply(privateKey: java.nio.file.Path): KeyFileSftpIdentity
}
```

Both accept the private key formats sshj reads, including OpenSSH, PEM (PKCS#1 and PKCS#8) and PuTTY keys. To log in with an encrypted key file:

```scala
import zio.ftp._
import java.nio.file.Paths

val keyFile = KeyFileSftpIdentity(Paths.get("/home/app/.ssh/id_ed25519"), Some("key passphrase"))

val settings = SecureFtpSettings("sftp.example.com", 22, KeyCredentials("app", keyFile))
```

To log in with a key held in an environment variable:

```scala
import zio.ftp._

val rawKey = RawKeySftpIdentity(sys.env("SFTP_PRIVATE_KEY"))

val settings = SecureFtpSettings("sftp.example.com", 22, KeyCredentials("app", rawKey))
```

## Related Pages

For FTP and FTPS, see [FTP and FTPS settings](unsecure-ftp-settings.md). For the differences between the protocols, see [FTP, FTPS and SFTP](../protocols.md).
