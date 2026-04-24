# ZIO FTP

> [ZIO FTP](https://zio.dev) is a thin wrapper over (s)Ftp client for [ZIO](https://zio.dev).

[ZIO FTP](https://zio.dev) is a thin wrapper over (s)Ftp client for [ZIO](https://zio.dev).

[![Production Ready](https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg)](https://github.com/zio/zio/wiki/Project-Stages) ![CI Badge](https://github.com/zio/zio-ftp/workflows/CI/badge.svg) [![Sonatype Releases](https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-ftp_2.13.svg?label=Sonatype%20Release)](https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-ftp_2.13/) [![Sonatype Snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/dev.zio/zio-ftp_2.13.svg?label=Sonatype%20Snapshot)](https://oss.sonatype.org/content/repositories/snapshots/dev/zio/zio-ftp_2.13/) [![javadoc](https://javadoc.io/badge2/dev.zio/zio-ftp-docs_2.13/javadoc.svg)](https://javadoc.io/doc/dev.zio/zio-ftp-docs_2.13) [![ZIO FTP](https://img.shields.io/github/stars/zio/zio-ftp?style=social)](https://github.com/zio/zio-ftp)

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-ftp" % "0.5.2" 
```

## How to use it?

* Imports
```scala

```

* FTP
```scala
// FTP
val unsecureSettings = UnsecureFtpSettings("127.0.0.1", 21, PasswordCredentials("foo", "bar"))

//listing files
Ftp.ls("/").runCollect.provideLayer(unsecure(unsecureSettings))
```

* FTPS
```scala
// FTPS
val secureSettings = SecureFtpSettings("127.0.0.1", 21, PasswordCredentials("foo", "bar"))

//listing files
SFtp.ls("/").runCollect.provideLayer(secure(secureSettings))
```

* SFTP (support ssh key)

```scala
val sftpSettings = SecureFtpSettings("127.0.0.1", 22, PasswordCredentials("foo", "bar"))

//listing files
SFtp.ls("/").runCollect.provideLayer(secure(sftpSettings))
```

## Example

First we need an FTP server, so let's create one:

```bash
docker run -d \
    -p 21:21 \
    -p 21000-21010:21000-21010 \
    -e USERS="one|1234" \
    -e ADDRESS=localhost \
    delfer/alpine-ftp-server
```

Now we can run the example:

```scala

object ZIOFTPExample extends ZIOAppDefault {

  private val settings =
    UnsecureFtpSettings("127.0.0.1", 21, PasswordCredentials("one", "1234"))

  private val myApp: ZIO[Ftp, IOException, Unit] =
    for {
      _        <- Console.printLine("List of files at root directory:")
      resource <- ls("/").runCollect
      _        <- ZIO.foreach(resource)(e => Console.printLine(e.path))
      path      = "~/file.txt"
      _        <- upload(
                    path,
                    ZStream.fromChunk(
                      Chunk.fromArray("Hello, ZIO FTP!\nHello, World!".getBytes)
                    )
                  )
      file     <- readFile(path)
                    .via(ZPipeline.utf8Decode)
                    .runCollect
      _        <- Console.printLine(s"Content of $path file:")
      _        <- Console.printLine(file.mkString)
    } yield ()

  override def run = myApp.provideSomeLayer(unsecure(settings))
}
```

## Support any commands?

If you need a method which is not wrapped by the library, you can have access to underlying FTP client in a safe manner by using

```scala

trait FtpAccessors[+A] {
  def execute[T](f: A => T): ZIO[Any, IOException, T]
} 
```
