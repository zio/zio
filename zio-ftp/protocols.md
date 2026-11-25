# FTP, FTPS and SFTP

> ZIO FTP talks to three kinds of file servers. They share a name and a purpose, but they are different protocols with different security properties, and a few operations behave differently on each. This page explains the differences so you can pick the right settings and know what to expect from each operation.

## The Three Protocols

FTP, FTPS and SFTP all move files between a client and a server, but they differ in transport and security:

- **FTP** is the original File Transfer Protocol. It sends commands over one TCP connection (the control connection) and file contents over a second one (the data connection). Nothing is encrypted, including the password.
- **FTPS** is FTP with TLS. The commands and replies are the same as FTP, but the control connection, and optionally the data connection, are encrypted. ZIO FTP supports explicit FTPS (the client upgrades a plain connection with `AUTH TLS`) and implicit FTPS (the connection is TLS from the start, usually on port 990).
- **SFTP** is the SSH File Transfer Protocol. Despite the name, it is not FTP at all: it is a subsystem of SSH, runs over a single encrypted SSH connection (port 22 by default), and authenticates with a password or an SSH key.

## How ZIO FTP Maps Them

ZIO FTP uses Apache Commons Net for FTP and FTPS, and sshj for SFTP. FTP and FTPS share one implementation, so they share a settings class, a layer and an environment type:

| Protocol | Settings                                 | Layer      | Environment | Accessors   | Java client              |
| -------- | ---------------------------------------- | ---------- | ----------- | ----------- | ------------------------ |
| FTP      | `UnsecureFtpSettings(host, port, creds)` | `unsecure` | `Ftp`       | `Ftp.*`     | Commons Net `FTPClient`  |
| FTPS     | `UnsecureFtpSettings.secure(...)`        | `unsecure` | `Ftp`       | `Ftp.*`     | Commons Net `FTPSClient` |
| SFTP     | `SecureFtpSettings(host, port, creds)`   | `secure`   | `SFtp`      | `SFtp.*`    | sshj `SFTPClient`        |
| None     | a local directory                        | `stub`     | `StubFtp`   | `StubFtp.*` | none (local file system) |

The last row is the test double: `StubFtp` runs the same operations against a local directory, so tests need no server. See [Test Without a Server](guides/test-without-a-server.md).

:::note
The names come from the code: `UnsecureFtpSettings` covers both FTP and FTPS, and "secure" in `SecureFtpSettings` and the `zio.ftp.secure` layer means SFTP. FTPS is selected by the `sslParams` field of `UnsecureFtpSettings`, not by the `zio.ftp.secure` layer.
:::

## Choosing a Protocol

Usually the server decides for you. When you have a choice, prefer SFTP: it needs one port, encrypts everything, and supports key-based login. Use FTPS when the server only offers FTP-family protocols but the data is sensitive, and set the data channel protection level to `Private`, because the default only encrypts the commands (see [Use FTPS](guides/use-ftps.md)). Use plain FTP only on trusted networks or for public data.

## Behavior Differences

All three implementations satisfy the same `FtpAccessors` interface, but they pass through the behavior of the underlying client. These differences are visible to your program:

| Operation or setting                                                   | FTP and FTPS                                                     | SFTP                                         | StubFtp                         |
| ---------------------------------------------------------------------- | ---------------------------------------------------------------- | -------------------------------------------- | ------------------------------- |
| `FtpAccessors#stat` on a missing path                                  | `None`                                                           | `None`                                       | `None`                          |
| `FtpAccessors#stat(...).isDirectory`                                   | `Some(true)` or `Some(false)`                                    | always `None`                                | `Some(true)` or `Some(false)`   |
| `FtpAccessors#mkdir("/a/b/c")`                                         | creates only the last level                                      | creates missing parents                      | creates missing parents         |
| `FtpAccessors#ls` on a missing directory                               | depends on the server; the tested servers return an empty stream | empty stream                                 | empty stream                    |
| `FtpAccessors#lsDescendant`                                            | files only, recursively                                          | files only, recursively                      | regular files only, recursively |
| `FtpAccessors#readFile` on a missing file                              | fails with `InvalidPathError`                                    | fails with sshj's `SFTPException`            | fails with `InvalidPathError`   |
| `FtpAccessors#rm`, `FtpAccessors#rmdir`, `FtpAccessors#rename` failure | fail with `InvalidPathError`                                     | fail with sshj's `SFTPException`             | fail with `IOException`         |
| Incomplete download                                                    | stream fails with `FileTransferIncompleteError`                  | stream fails with the client's `IOException` | not applicable                  |
| TCP keepalive                                                          | `keepalive` setting                                              | not configurable                             | not applicable                  |
| Timeouts                                                               | `dataTimeout`, `defaultTimeout`                                  | not configurable (sshj defaults)             | not applicable                  |

Every failure is an `IOException`, so code that handles `IOException` works with all three. Code that matches on `InvalidPathError` only catches FTP failures and `StubFtp.readFile` failures; see [Errors](reference/errors.md).

## One Connection, One Conversation

Each layer holds exactly one connection, and every operation in the program shares it. FTP in particular is a strict request and reply conversation on one control connection: while an `FtpAccessors#readFile` stream is open, the server is busy with that transfer, and the stream only finishes the command when it is fully consumed or closed. Run operations on the same `Ftp` service one after another, not in parallel, and consume or close each `FtpAccessors#readFile` stream before starting the next operation.

If you need parallel transfers, build one layer per connection, for example with `ZIO.scoped` and `UnsecureFtp.connect` for each worker. [Connecting](reference/connecting.md) shows the scoped constructors.

## Security Defaults

Two defaults favor getting started over security, and you should change both for production. First, `SecureFtpSettings.apply` sets `strictHostKeyChecking = false`, so the SFTP client accepts any host key and cannot detect a man-in-the-middle; turn it on and point `knownHosts` at a `known_hosts` file, as shown in [Connect over SFTP with a Key](guides/connect-over-sftp-with-a-key.md). Second, `SslParams.default` sets the FTPS data channel protection level to `Clear`, so file contents travel unencrypted even though the commands and password are encrypted; use `ProtectionLevel.Private`, as shown in [Use FTPS](guides/use-ftps.md).
