# FTP and FTPS Settings

> `UnsecureFtpSettings` describes a connection to an FTP or FTPS server. Despite the name, it covers both: a value with `sslParams` set connects over TLS. This page documents every field, the two constructors, and the supporting types `SslParams`, `ProtectionLevel`, `KeepaliveSettings` and `PasswordCredentials`.

## Definition

`UnsecureFtpSettings` is a case class with twelve fields. The first seven have no default value, and the last five do:

```scala
final case class UnsecureFtpSettings(
  host: String,
  port: Int,
  credentials: PasswordCredentials,
  binary: Boolean,
  passiveMode: Boolean,
  remoteVerificationEnabled: Boolean,
  proxy: Option[java.net.Proxy],
  sslParams: Option[SslParams] = None,
  dataTimeout: Option[Duration] = Some(1.minute),
  defaultTimeout: Option[Duration] = Some(1.minute),
  keepalive: Option[KeepaliveSettings] = Some(KeepaliveSettings.default),
  controlEncoding: Option[String] = None
)
```

The settings value is passed to the `unsecure` layer or to `UnsecureFtp.connect`, both documented in [Connecting](connecting.md).

## Construction

Use the companion's constructors rather than the case class constructor. They fill in the fields without defaults:

```scala
object UnsecureFtpSettings {
  def apply(host: String, port: Int, creds: PasswordCredentials): UnsecureFtpSettings
  def secure(host: String, port: Int, creds: PasswordCredentials): UnsecureFtpSettings
}
```

`UnsecureFtpSettings.apply` creates plain FTP settings with binary transfers, passive mode and remote verification turned on, no proxy, and the case class defaults for the rest:

```scala
import zio.ftp._

UnsecureFtpSettings("127.0.0.1", 21, PasswordCredentials("one", "1234"))
// res0: UnsecureFtpSettings = UnsecureFtpSettings(
//   host = "127.0.0.1",
//   port = 21,
//   credentials = PasswordCredentials(username = "one", password = "1234"),
//   binary = true,
//   passiveMode = true,
//   remoteVerificationEnabled = true,
//   proxy = None,
//   sslParams = None,
//   dataTimeout = Some(value = PT1M),
//   defaultTimeout = Some(value = PT1M),
//   keepalive = Some(
//     value = KeepaliveSettings(
//       idle = Some(value = 60),
//       interval = Some(value = 10),
//       count = Some(value = 5)
//     )
//   ),
//   controlEncoding = None
// )
```

`UnsecureFtpSettings.secure` creates the same settings with `sslParams = Some(SslParams.default)`, which selects explicit FTPS:

```scala
UnsecureFtpSettings.secure("127.0.0.1", 21, PasswordCredentials("one", "1234")).sslParams
// res1: Option[SslParams] = Some(
//   value = SslParams(isImplicit = false, pbzs = 0L, prot = Clear)
// )
```

To change any other field, use `UnsecureFtpSettings#copy` on the result.

## Fields

The fields fall into four groups: where to connect, how to transfer, how to secure the connection, and how to keep it healthy.

### Server and Credentials

The `host` field is the server's host name or IP address, and `port` is its control port, usually 21 for FTP and explicit FTPS and 990 for implicit FTPS. The `credentials` field is a `PasswordCredentials(username, password)` value; FTP has no key-based login. For anonymous FTP, servers conventionally accept the user name `anonymous` with an email address as the password.

The `proxy` field routes every socket through a `java.net.Proxy`, such as a SOCKS proxy, when it is `Some`:

```scala
import zio.ftp._
import java.net.{ InetSocketAddress, Proxy }

val viaSocks = UnsecureFtpSettings("ftp.example.com", 21, PasswordCredentials("one", "1234"))
  .copy(proxy = Some(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("proxy.internal", 1080))))
```

### Transfer Mode

The `binary` field selects the transfer type. When it is true, the client sends `TYPE I` after login and files are transferred byte for byte. When it is false, the client keeps the server's default, which is ASCII mode on most servers and may rewrite line endings. Keep it true unless you know you need ASCII conversion.

The `passiveMode` field selects passive mode, where the client opens the data connection to a port the server announces. When it is false, the client uses active mode, where the server connects back to the client. Passive mode works through client-side firewalls and NAT, which is why both constructors turn it on.

The `remoteVerificationEnabled` field asks the client to check that each data connection comes from the same host as the control connection. Commons Net enables this check by default, and ZIO FTP only calls its setter when the field is true, so setting the field to false does not currently turn the check off.

The `controlEncoding` field sets the character encoding of the control connection, which carries commands and file names. When it is `None`, the client asks the server whether it supports UTF-8 and uses it if so. Set it, for example to `Some("ISO-8859-1")`, for servers with non-ASCII file names in another encoding.

### TLS

The `sslParams` field turns on FTPS when it is `Some`. Its value is an `SslParams`:

```scala
final case class SslParams(isImplicit: Boolean, pbzs: Long, prot: ProtectionLevel)

object SslParams {
  val default: SslParams = SslParams(isImplicit = false, 0L, ProtectionLevel.Clear)
}
```

The three parameters map to the FTPS connection setup:

- `isImplicit` selects implicit FTPS when true, where the connection is TLS from the first byte, and explicit FTPS when false, where the client connects in plain text and upgrades with `AUTH TLS`.
- `pbzs` is the protection buffer size sent with the `PBSZ` command after login. With TLS it is always 0.
- `prot` is the data channel protection level sent with the `PROT` command.

`ProtectionLevel` has one case per `PROT` value:

| Case                           | `PROT` value | Meaning                                      |
| ------------------------------ | ------------ | -------------------------------------------- |
| `ProtectionLevel.Clear`        | `C`          | data connections are not encrypted           |
| `ProtectionLevel.Private`      | `P`          | data connections are encrypted with TLS      |
| `ProtectionLevel.Safe`         | `S`          | integrity protection only, not used with TLS |
| `ProtectionLevel.Confidential` | `E`          | confidentiality only, not used with TLS      |

FTPS servers normally accept only `C` and `P`. Since `SslParams.default` uses `Clear`, file contents are not encrypted unless you choose `Private`:

```scala
import zio.ftp._

val encrypted = UnsecureFtpSettings
  .secure("ftp.example.com", 21, PasswordCredentials("one", "1234"))
  .copy(sslParams = Some(SslParams(isImplicit = false, pbzs = 0L, prot = ProtectionLevel.Private)))
```

[Use FTPS](../guides/use-ftps.md) walks through explicit and implicit mode.

### Timeouts and Keepalive

Two timeouts and a keepalive option protect long-running connections. Each one is turned off by setting it to `None`:

- `dataTimeout` is how long a read on a data connection can block before it fails. It defaults to one minute.
- `defaultTimeout` is the read timeout of the control connection, which bounds how long the client waits for a command reply. It doesn't limit the initial TCP connection attempt. It defaults to one minute.
- `keepalive` turns on TCP keepalive on every socket the client opens when it is `Some`. It defaults to `Some(KeepaliveSettings.default)`.

`KeepaliveSettings` holds the three TCP keepalive parameters, each in the units of `jdk.net.ExtendedSocketOptions`:

```scala
final case class KeepaliveSettings(idle: Option[Int], interval: Option[Int], count: Option[Int])

object KeepaliveSettings {
  val default: KeepaliveSettings = KeepaliveSettings(Some(60), Some(10), Some(5))
}
```

`idle` is the number of seconds a connection must be idle before the first probe, `interval` is the number of seconds between probes, and `count` is the number of unanswered probes after which the connection is dropped. A `None` parameter keeps the operating system's default. If the platform does not support one of the options, ZIO FTP logs a warning with `ZIO.logWarning` and continues without it. To set both timeouts and all three keepalive parameters at once:

```scala
import zio._
import zio.ftp._

val tunedSettings = UnsecureFtpSettings("127.0.0.1", 21, PasswordCredentials("foo", "bar")).copy(
  dataTimeout = Some(30.seconds),
  defaultTimeout = Some(10.seconds),
  keepalive = Some(KeepaliveSettings(idle = Some(30), interval = Some(5), count = Some(3)))
)
```

[Keep a Connection Alive](../guides/keep-a-connection-alive.md) explains when each setting matters.

## Related Pages

For SFTP, see [SFTP settings](secure-ftp-settings.md). For what happens with these fields when the connection opens, see the lifecycle section of [Connecting](connecting.md#connection-lifecycle).
