# Connect over SFTP with a Key

> Log in to an SFTP server with an SSH key from a file or a secret, and verify the server's host key against known_hosts.

In this guide, we connect to an SFTP server with an SSH key instead of a password, load the key either from a file or from a secret, and turn on host key checking so the client refuses servers it doesn't know.

## The Problem

Password logins are weak for unattended jobs: the password ends up in configuration, and many servers disable it. SSH keys fix that, but they bring two decisions of their own: where the private key comes from, and how the client knows it is talking to the real server. The shortest setup, a password login that accepts any host key, is fine for a local container and wrong for production:

```scala
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier

// Works, but sends a password and trusts whatever server answers.
val ssh = new SSHClient()
ssh.addHostKeyVerifier(new PromiscuousVerifier())
ssh.connect("sftp.example.com", 22)
ssh.authPassword("app", "s3cret")
```

`SecureFtpSettings.apply` also skips the host key check, and `PasswordCredentials` sends a password, so this guide changes both.

## Prerequisites

You need the `zio-ftp` dependency from [Getting Started](../getting-started.md), the OpenSSH command-line tools (`ssh-keygen`, `ssh-keyscan`), and an account on the SFTP server. The examples share these imports:

```scala
import zio._
import zio.ftp._
import java.io.IOException
import java.nio.file.Paths
```

## Creating and Installing a Key Pair

If you don't have a key pair for the job yet, create one. The following command writes a private key to `~/.ssh/zio_ftp_ed25519` and its public key to `~/.ssh/zio_ftp_ed25519.pub`, protected by the passphrase you type:

```bash
ssh-keygen -t ed25519 -f ~/.ssh/zio_ftp_ed25519 -C "zio-ftp backup job"
```

Install the public key on the server by appending it to the account's `~/.ssh/authorized_keys` file, for example with `ssh-copy-id -i ~/.ssh/zio_ftp_ed25519.pub app@sftp.example.com`, or ask the server's administrator to add it.

## Logging In with a Key File

`KeyCredentials` takes the user name and an `SftpIdentity`. For a key stored on disk, the identity is a `KeyFileSftpIdentity` with the key's path and, for an encrypted key, its passphrase:

```scala
val keyFile = KeyFileSftpIdentity(
  Paths.get(sys.props("user.home"), ".ssh", "zio_ftp_ed25519"),
  sys.env.get("SFTP_KEY_PASSPHRASE")
)

val keySettings = SecureFtpSettings("sftp.example.com", 22, KeyCredentials("app", keyFile))
```

Passing `None` as the passphrase works for an unencrypted key. The client reads the key when the connection opens, so a wrong path or passphrase fails the `secure` layer with a `ConnectionError`.

## Logging In with a Key from a Secret

In containers and CI, the key usually comes from a secret store or an environment variable rather than a file. `RawKeySftpIdentity` takes the private key as text, in the same format as the key file:

```scala
val secretKey = RawKeySftpIdentity(
  privateKey = sys.env.getOrElse("SFTP_PRIVATE_KEY", ""),
  passphrase = sys.env.get("SFTP_KEY_PASSPHRASE")
)

val secretSettings = SecureFtpSettings("sftp.example.com", 22, KeyCredentials("app", secretKey))
```

`RawKeySftpIdentity` also has an optional `publicKey` field, which is only needed for key formats that don't embed the public key. OpenSSH keys from `ssh-keygen` embed it.

## Verifying the Server's Host Key

With the settings above, the client still accepts any host key. To refuse unknown servers, set `strictHostKeyChecking` to true and point `knownHosts` at an OpenSSH `known_hosts` file. The simplest source for that file is your own `~/.ssh/known_hosts` after one manual `ssh` or `sftp` login. For a dedicated file, collect the key with `ssh-keyscan` and check its fingerprint with the server's administrator before trusting it:

```bash
ssh-keyscan -t ed25519 sftp.example.com > config/known_hosts
ssh-keygen -lf config/known_hosts
```

Then add the two fields to the settings:

```scala
val strictSettings = keySettings.copy(
  strictHostKeyChecking = true,
  knownHosts = Some("config/known_hosts")
)
```

If the server presents a key that isn't in the file, or the file can't be read, the connection fails with a `ConnectionError`. With `strictHostKeyChecking = true` and `knownHosts = None`, no key is trusted and every connection fails.

## Checking the Connection

To confirm that the key and the host key are accepted, run a cheap operation such as `SFtp.stat("/")` with the `secure` layer:

```scala
object CheckSftp extends ZIOAppDefault {
  override def run =
    SFtp
      .stat("/")
      .flatMap(root => Console.printLine(s"Connected; root exists: ${root.isDefined}"))
      .provide(secure(strictSettings))
}
```

A `ConnectionError` here carries the original sshj exception as its cause, whose message tells a rejected key (`Exhausted available authentication methods`) from an unknown host key (`Could not verify ... host key`).

## Going Further

For every field of `SecureFtpSettings` and both identity types, see [SFTP settings](../reference/secure-ftp-settings.md). For the difference between SFTP and FTPS, see [FTP, FTPS and SFTP](../protocols.md).
