# Call an Unwrapped Client Method

> Run FTP and SFTP commands that ZIO FTP doesn't wrap, such as SITE CHMOD, symbolic links or the working directory, safely with execute.

In this guide, we run commands that ZIO FTP doesn't wrap, such as changing file permissions, reading the working directory or creating symbolic links, by calling the underlying Java client through `FtpAccessors#execute`. The calls stay on ZIO's blocking thread pool and fail with typed errors, like the built-in operations.

## The Problem

ZIO FTP wraps the common file operations, but FTP and SFTP have many more: `SITE` commands, modification times, permissions, links. Calling the Java client directly from a fiber blocks one of ZIO's main threads and throws exceptions outside ZIO's error channel:

```scala
import org.apache.commons.net.ftp.FTPClient

// Blocks the calling thread and throws on network errors.
def chmod(client: FTPClient, path: String): Boolean =
  client.sendSiteCommand(s"CHMOD 644 $path")
```

`FtpAccessors#execute` takes a function of the client and runs it with `ZIO.attemptBlockingIO`, so the call moves to the blocking pool and an `IOException` becomes a failure of the effect.

## Prerequisites

You need the `zio-ftp` dependency from [Getting Started](../getting-started.md). The examples share these imports:

```scala
import zio._
import zio.ftp._
import java.io.IOException
```

## Calling an FTP Client Method

For `Ftp`, the function receives the Commons Net `FTPClient`. `Ftp.execute` takes the connection from the environment:

```scala
val workingDirectory: ZIO[Ftp, IOException, String] =
  Ftp.execute(_.printWorkingDirectory())
```

Many `FTPClient` methods return `false` instead of throwing when the server refuses a command, so check the result and turn a refusal into a failure. `FTPClient#getReplyString` holds the server's last reply:

```scala
def chmod(path: String, mode: String): ZIO[Ftp, IOException, Unit] =
  Ftp.execute { client =>
    if (client.sendSiteCommand(s"CHMOD $mode $path")) None else Some(client.getReplyString.trim)
  }.flatMap {
    case None        => ZIO.unit
    case Some(reply) => ZIO.fail(new IOException(s"CHMOD $mode $path refused: $reply"))
  }
```

Read the reply inside the same `FtpAccessors#execute` call as the command, as above. A later call might run after another command has replaced the reply.

## Calling an SFTP Client Method

For `SFtp`, the function receives the sshj `SFTPClient`, whose methods throw an `SFTPException` (an `IOException`) when the server refuses, so no result check is needed:

```scala
val home: ZIO[SFtp, IOException, String] =
  SFtp.execute(_.canonicalize("."))

def makePrivate(path: String): ZIO[SFtp, IOException, Unit] =
  SFtp.execute(_.chmod(path, Integer.parseInt("600", 8)))

def link(target: String, linkPath: String): ZIO[SFtp, IOException, Unit] =
  SFtp.execute(_.symlink(target, linkPath))
```

`SFTPClient#canonicalize` with `"."` returns the absolute path of the login directory, which helps when the server's `/` is not what you expect. On OpenSSH servers, `SFTPClient#symlink` takes the link's target first and the new link's path second.

## Reading Modification Times Exactly

On FTP, listings often show only the minute of a file's modification time. The `MDTM` command returns it to the second. The following function returns the exact time, or `None` if the file doesn't exist:

```scala
def modifiedAt(path: String): ZIO[Ftp, IOException, Option[java.time.Instant]] =
  Ftp.execute(client => Option(client.mdtmInstant(path)))
```

## Knowing What execute Catches

`FtpAccessors#execute` turns `IOException` into a failure. Any other exception, such as an `IllegalArgumentException` from a bad argument, is a defect that ends the fiber rather than a failure you can handle with `ZIO#catchAll`. Validate arguments before the call, or catch the exception inside the function and return it as a value. To validate an argument before the call, fail with an `IOException` of your own:

```scala
def chmodOctal(path: String, mode: String): ZIO[SFtp, IOException, Unit] =
  ZIO
    .attempt(Integer.parseInt(mode, 8))
    .mapError(e => new IOException(s"Invalid mode $mode", e))
    .flatMap(bits => SFtp.execute(_.chmod(path, bits)))
```

`FtpAccessors#execute` also doesn't add synchronization: the function runs on the same connection as every other operation. On FTP, don't call `Ftp.execute` while an `Ftp.readFile` stream on the same connection is still open, and don't run it in parallel with other operations on that connection.

## Going Further

For the Java clients' full APIs, see the [Commons Net `FTPClient`](https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPClient.html) and [sshj](https://github.com/hierynomus/sshj) documentation. For `FtpAccessors#execute` alongside the other operations, see the [Ftp API](../reference/ftp-accessors.md#running-unwrapped-client-calls) reference.
