# Use FTPS

> Connect to an FTPS server in explicit or implicit mode and encrypt file contents as well as commands with PROT P.

In this guide, we connect to an FTP server over TLS (FTPS), make sure file contents are encrypted and not only the commands, and switch between explicit and implicit mode.

## The Problem

Plain FTP sends the password and every file in clear text. FTPS adds TLS, but its default is easy to get wrong in a way that still "works": the data channel can stay unencrypted while the commands are encrypted. The shortest FTPS setup does exactly that:

```scala
import org.apache.commons.net.ftp.FTPSClient

// Encrypts the commands and the password, but not the file contents.
val client = new FTPSClient(false)
client.connect("ftp.example.com", 21)
client.login("one", "1234")
```

`UnsecureFtpSettings.secure` keeps the same default, `PROT C`, through `SslParams.default`.

This guide fixes that, and shows how to choose between the two FTPS modes.

## Prerequisites

You need the `zio-ftp` dependency from [Getting Started](../getting-started.md) and an FTP server with TLS enabled. To try it locally, the Pure-FTPd image from Getting Started accepts TLS when you add `-e ADDED_FLAGS="--tls=1"` and mount a certificate at `/etc/ssl/private/pure-ftpd.pem`, which is how ZIO FTP's own tests run it. The examples share these imports:

```scala
import zio._
import zio.ftp._
import java.io.IOException
```

## Connecting with Explicit FTPS

Explicit FTPS, also called FTPES, connects to the usual FTP port and upgrades the connection with `AUTH TLS` before logging in. It is the common mode today. `UnsecureFtpSettings.secure` selects it, and we set the data channel protection level to `Private` so file transfers are encrypted too:

```scala
val explicitTls = UnsecureFtpSettings
  .secure("ftp.example.com", 21, PasswordCredentials("one", "1234"))
  .copy(sslParams = Some(SslParams(isImplicit = false, pbzs = 0L, prot = ProtectionLevel.Private)))
```

After login, the client sends `PBSZ 0` and `PROT P`. With the default, `ProtectionLevel.Clear`, it sends `PROT C`, and the data connections that carry listings and file contents are not encrypted. Everything else works as with plain FTP: provide `unsecure(explicitTls)` and use the `Ftp` accessors.

## Connecting with Implicit FTPS

Implicit FTPS is the older mode: the connection is TLS from the first byte, on a dedicated port, usually 990. Set `isImplicit` to true and use that port:

```scala
val implicitTls = UnsecureFtpSettings
  .secure("ftp.example.com", 990, PasswordCredentials("one", "1234"))
  .copy(sslParams = Some(SslParams(isImplicit = true, pbzs = 0L, prot = ProtectionLevel.Private)))
```

If the connection hangs or fails during the handshake, you probably have the mode wrong for the port: explicit mode on port 990, or implicit mode on port 21.

## Going Further

For every TLS field and protection level, see [FTP and FTPS settings](../reference/unsecure-ftp-settings.md#tls). For when to prefer SFTP over FTPS, see [FTP, FTPS and SFTP](../protocols.md#choosing-a-protocol).
