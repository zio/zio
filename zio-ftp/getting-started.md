# Getting Started with ZIO FTP

> This page takes you from an empty sbt project to a program that uploads a file to an FTP server, lists a directory and reads the file back. By the end, you will know the three pieces every ZIO FTP program has: settings, a layer and operations.

## Prerequisites

You need sbt, a JDK (ZIO FTP is tested on Java 17 and 21), and Docker to run a local FTP server. Some familiarity with `ZIO` effects and `ZStream` helps, but the program below is short enough to follow without it.

## Add the Dependency

ZIO FTP is published for Scala 2.12, 2.13 and 3 on the JVM. Add it to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  // FTP, FTPS and SFTP clients as ZIO effects and streams (JVM only)
  "dev.zio" %% "zio-ftp" % "0.5.4"
)
```

The library depends on ZIO and ZIO Streams, so you don't need to add them separately.

## Start a Local FTP Server

We use the same Pure-FTPd image that ZIO FTP's own test suite runs against. It creates a user `username` with password `userpass` and listens on port 2121:

```bash
docker run -d --name zio-ftp-demo \
  -p 2121:21 -p 30000-30009:30000-30009 \
  -e PUBLICHOST=localhost \
  -e FTP_USER_NAME=username \
  -e FTP_USER_PASS=userpass \
  -e FTP_USER_HOME=/home/username \
  stilliard/pure-ftpd
```

Ports 30000 to 30009 carry the data connections, which the server opens in passive mode. ZIO FTP uses passive mode by default, which is what most servers behind a firewall or NAT need.

## Describe the Connection

Every connection starts with a settings value. For plain FTP it is `UnsecureFtpSettings`, and its three-argument `UnsecureFtpSettings.apply` takes the host, the port and the credentials:

```scala
import zio.ftp._

val settings = UnsecureFtpSettings("127.0.0.1", 2121, PasswordCredentials("username", "userpass"))
```

The other fields get sensible defaults: binary transfers, passive mode, one-minute timeouts and TCP keepalive. The [FTP and FTPS settings](reference/unsecure-ftp-settings.md) reference lists all of them.

## Write the Program

Operations live on the `Ftp` object. Each one returns a `ZIO` or a `ZStream` that needs an `Ftp` service in its environment, so the program describes what to do without connecting yet:

```scala
import zio._
import zio.stream._
import java.io.IOException

val program: ZIO[Ftp, IOException, Unit] =
  for {
    _       <- Ftp.upload("/hello.txt", ZStream.fromChunk(Chunk.fromArray("Hello, ZIO FTP!".getBytes)))
    files   <- Ftp.ls("/").runCollect
    _       <- ZIO.foreachDiscard(files)(f => Console.printLine(s"${f.path} (${f.size} bytes)"))
    content <- Ftp.readFile("/hello.txt").via(ZPipeline.utf8Decode).runCollect
    _       <- Console.printLine(content.mkString)
  } yield ()
```

`Ftp.upload` takes the bytes as a `ZStream`, so the same call works for a short string and for a multi-gigabyte file. `Ftp.ls` and `Ftp.readFile` return streams too: `ZStream#runCollect` gathers the elements once we want them all.

## Provide the Connection

The `unsecure` function turns the settings into a `ZLayer` that connects and logs in when it is built, and logs out and disconnects when the program ends. Providing it removes `Ftp` from the program's requirements:

```scala
object GettingStarted extends ZIOAppDefault {
  override def run = program.provide(unsecure(settings))
}
```

Run it with `sbt run`. The program prints `/hello.txt (15 bytes)` among the files in the user's home directory, followed by `Hello, ZIO FTP!`. If the server refuses the login, the layer fails with a `ConnectionError` and nothing else runs.

## Switch to SFTP

SFTP servers run over SSH, so they use `SecureFtpSettings`, the `SFtp` accessors and the `secure` layer. The operations are the same. To try it, start an SFTP server whose user `foo` has a writable `upload` directory:

```bash
docker run -d --name zio-sftp-demo -p 2222:22 atmoz/sftp foo:pass:::upload
```

Then change the settings, the accessor object and the layer, and keep the rest:

```scala
import zio._
import zio.ftp._
import zio.stream._

val sftpSettings = SecureFtpSettings("127.0.0.1", 2222, PasswordCredentials("foo", "pass"))

val sftpProgram =
  for {
    _     <- SFtp.upload("/upload/hello.txt", ZStream.fromChunk(Chunk.fromArray("Hello, SFTP!".getBytes)))
    names <- SFtp.ls("/upload").map(_.path).runCollect
    _     <- ZIO.foreachDiscard(names)(Console.printLine(_))
  } yield ()

val runnable = sftpProgram.provide(secure(sftpSettings))
```

:::warning
`SecureFtpSettings.apply` turns host key checking off, so the client accepts any server key. That is fine for a local container, but connect to real servers with strict checking, as shown in [Connect over SFTP with a Key](guides/connect-over-sftp-with-a-key.md).
:::

## Clean Up

To stop and remove the demo servers:

```bash
docker rm -f zio-ftp-demo zio-sftp-demo
```

## Next Steps

From here, you can:

- Read [FTP, FTPS and SFTP](protocols.md) to choose a protocol and learn where their behavior differs.
- Read [Environment](environment.md) to see how the connection layer fits into a larger ZIO application.
- Follow the [tutorial](tutorials/back-up-a-folder.md) to build a small backup tool.
- Browse the [Ftp API](reference/ftp-accessors.md) reference for every operation.
