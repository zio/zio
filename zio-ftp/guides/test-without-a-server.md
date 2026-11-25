# Test Without a Server

> Test code that uses ZIO FTP with ZIO Test and the StubFtp test double, backed by a temporary local directory instead of a server.

In this guide, we test code that uploads and reads files with ZIO FTP, without starting an FTP or SFTP server. We write the code so the connection can be swapped, run it against the `StubFtp` test double in ZIO Test, and give each test a fresh temporary directory.

## The Problem

Code that talks to a file server is awkward to test: the tests need a running server, credentials, network ports and cleanup between runs. ZIO FTP's own integration tests start three Docker containers. Code that takes the real client as a parameter can only be tested against a real server:

```scala
import org.apache.commons.net.ftp.FTPClient
import java.io.ByteArrayInputStream

def publishReport(client: FTPClient, csv: String): Boolean =
  client.makeDirectory("/reports/2026-11") &&
    client.storeFile("/reports/2026-11/19.csv", new ByteArrayInputStream(csv.getBytes("UTF-8")))
```

For your application's logic, such as "the report is uploaded to the right folder with the right content", a real server adds slowness and flakiness without testing anything of yours.

ZIO FTP includes a test double, `TestFtp`, that implements every operation on a local directory. To use it, the code under test must not be tied to the `Ftp` or `SFtp` environment types.

## Prerequisites

You need the `zio-ftp` dependency from [Getting Started](../getting-started.md) and ZIO Test:

```scala
libraryDependencies ++= Seq(
  // FTP, FTPS and SFTP clients as ZIO effects and streams (JVM only)
  "dev.zio" %% "zio-ftp" % "0.5.4",

  // ZIO's test framework
  "dev.zio" %% "zio-test" % "2.1.11" % Test,

  // runs ZIO Test specs from sbt
  "dev.zio" %% "zio-test-sbt" % "2.1.11" % Test
)
```

Use the ZIO version your project already depends on for the two test artifacts.

## Writing Code That Accepts Any Connection

`Ftp`, `SFtp` and `StubFtp` are all `FtpAccessors` with a different client type. Code that takes an `FtpAccessors[Any]` parameter works with all three, because `FtpAccessors` is covariant in its client type. The following function publishes a daily report: it creates the folder for the month and uploads the report into it:

```scala
import zio._
import zio.ftp._
import zio.stream._
import java.io.IOException

def publishReport(ftp: FtpAccessors[Any], day: java.time.LocalDate, csv: String): ZIO[Any, IOException, String] = {
  val dir  = f"/reports/${day.getYear}%04d-${day.getMonthValue}%02d"
  val path = s"$dir/${day.getDayOfMonth}.csv"
  for {
    existing <- ftp.stat(dir)
    _        <- ftp.mkdir(dir).when(existing.isEmpty)
    _        <- ftp.upload(path, ZStream.fromChunk(Chunk.fromArray(csv.getBytes("UTF-8"))))
  } yield path
}
```

In the application, take the real connection from the environment and pass it in:

```scala
def publishToServer(day: java.time.LocalDate, csv: String): ZIO[Ftp, IOException, String] =
  ZIO.serviceWithZIO[Ftp](publishReport(_, day, csv))
```

## Creating a Temporary Stub Server

Each test should start with an empty "server", so tests don't see each other's files. The following layer creates a temporary directory, builds the stub on top of it, and deletes the directory when the test ends:

```scala
import java.nio.file.{ Files, Path }
import java.util.Comparator

val tempStub: ZLayer[Any, Throwable, StubFtp] =
  ZLayer.scoped {
    ZIO
      .acquireRelease(ZIO.attemptBlockingIO(Files.createTempDirectory("stub-ftp"))) { dir =>
        ZIO.attemptBlockingIO {
          Files.walk(dir).sorted(Comparator.reverseOrder[Path]()).forEach(p => Files.delete(p))
        }.ignore
      }
      .map(dir => TestFtp.create(zio.nio.file.Path.fromJava(dir)))
  }
```

`TestFtp.create` takes a ZIO NIO path, so `zio.nio.file.Path.fromJava` converts the Java one.

:::note
Open pull request [#464](https://github.com/zio/zio-ftp/pull/464) proposes removing the ZIO NIO dependency. If it is merged, `TestFtp.create` and `zio.ftp.stub` take a `java.nio.file.Path` directly and the `zio.nio.file.Path.fromJava` call goes away.
:::

## Writing the Test

A test takes the stub from the environment, runs the code under test with it, and checks the result through the same stub:

```scala
import zio.test._

object PublishReportSpec extends ZIOSpecDefault {
  private val day = java.time.LocalDate.of(2026, 11, 19)

  def spec =
    suite("publishReport")(
      test("uploads the report into the month folder") {
        for {
          ftp     <- ZIO.service[StubFtp]
          path    <- publishReport(ftp, day, "id,amount\n1,9.99\n")
          content <- ftp.readFile(path).via(ZPipeline.utf8Decode).runCollect.map(_.mkString)
        } yield assertTrue(path == "/reports/2026-11/19.csv", content == "id,amount\n1,9.99\n")
      },
      test("reuses an existing month folder") {
        for {
          ftp   <- ZIO.service[StubFtp]
          _     <- publishReport(ftp, day, "a")
          _     <- publishReport(ftp, day.minusDays(1), "b")
          files <- ftp.ls("/reports/2026-11").map(_.path).runCollect
        } yield assertTrue(files.sorted == Chunk("/reports/2026-11/18.csv", "/reports/2026-11/19.csv"))
      }
    ).provide(tempStub)
}
```

`Spec#provide` with `tempStub` builds a fresh layer, and so a fresh directory, for each test. Run the spec with `sbt test` as usual.

## Seeing the Result

The stub needs no server, so the same check runs anywhere. Here it runs on this page, against a fresh stub:

```scala
Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(
    ZIO.serviceWithZIO[StubFtp] { ftp =>
      publishReport(ftp, java.time.LocalDate.of(2026, 11, 19), "id,amount\n") *>
        ftp.lsDescendant("/").map(_.path).runCollect
    }.provide(tempStub)
  ).getOrThrowFiberFailure()
}
// res1: Chunk[String] = IndexedSeq("/reports/2026-11/19.csv")
```

## Knowing the Stub's Limits

The stub follows the local file system, so a few behaviors differ from real servers. `FtpAccessors#mkdir` creates missing parent directories, as on SFTP but not on FTP; `FtpAccessors#readFile` ignores `chunkSize`; and, except for `FtpAccessors#readFile` on a missing file, errors are plain `IOException` values where FTP raises `InvalidPathError`. The first difference means a nested folder needs only one call on the stub, but one call per level on FTP:

```scala
val nested: ZIO[StubFtp, IOException, Unit] =
  ZIO.serviceWithZIO[StubFtp](_.mkdir("/archive/2026/11"))
```

Keep at least one integration test against a real server for behavior that depends on the protocol. The [TestFtp](../reference/test-ftp.md) reference lists every difference.

## Going Further

For the operations the stub implements, see the [Ftp API](../reference/ftp-accessors.md). For a longer example built on the stub, see the [tutorial](../tutorials/back-up-a-folder.md).
