---
id: zio-ftp
title: "ZIO FTP"
---

[ZIO FTP](https://github.com/zio/zio-ftp) is a simple, idiomatic (S)FTP client for ZIO.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-ftp" % "0.3.0" 
```

## Example

First we need an FTP server if we don't have:

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
import zio.blocking.Blocking
import zio.console.putStrLn
import zio.ftp.Ftp._
import zio.ftp._
import zio.stream.{Transducer, ZStream}
import zio.{Chunk, ExitCode, URIO, ZIO}

object ZIOFTPExample extends zio.App {
  private val settings =
    UnsecureFtpSettings("127.0.0.1", 21, FtpCredentials("one", "1234"))

  private val myApp = for {
    _        <- putStrLn("List of files at root directory:")
    resource <- ls("/").runCollect
    _        <- ZIO.foreach(resource)(e => putStrLn(e.path))
    path = "~/file.txt"
    _ <- upload(
      path,
      ZStream.fromChunk(
        Chunk.fromArray("Hello, ZIO FTP!\nHello, World!".getBytes)
      )
    )
    file <- readFile(path)
      .transduce(Transducer.utf8Decode)
      .runCollect
    _ <- putStrLn(s"Content of $path file:")
    _ <- putStrLn(file.fold("")(_ + _))
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = myApp
    .provideCustom(
      unsecure(settings) ++ Blocking.live
    )
    .exitCode
}
```
