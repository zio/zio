package zio.test.results

import zio._

import java.io.IOException
import scala.io.Source

private[test] object ResultFileOpsCsv {
  val live: ZLayer[Any, Nothing, ResultFileOps] =
    ZLayer.scoped(
      Live.apply
    )

  private[test] case class Live(resultPath: String, lock: Ref.Synchronized[Unit]) extends ResultFileOps {
    def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit] =
      lock.updateZIO(_ =>
        ZIO
          .acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(resultPath, append)))(f =>
            ZIO.attemptBlocking(f.close()).orDie
          ) { f =>
            ZIO.attemptBlockingIO(f.append(content))
          }
          .ignore
      )

    private val makeOutputDirectory = ZIO.attempt {
      import java.nio.file.{Files, Paths}

      val fp = Paths.get(resultPath)
      Files.createDirectories(fp.getParent)
    }.unit

    private def writeHeader: URIO[Any, Unit] =
      write(
        "name, status, durationMillis, annotations, fullyQualifiedClassName, labels",
        append = false
      ).orDie
  }

  object Live {
    def apply: ZIO[Scope, Nothing, Live] =
      ZIO.acquireRelease(
        for {
          fileLock <- Ref.Synchronized.make[Unit](())
          instance  = Live("target/test-reports-zio/output.json", fileLock)
          _        <- instance.makeOutputDirectory.orDie
          _        <- instance.writeHeader
        } yield instance
      )(_ => ZIO.unit) // TODO Is there a simpler method that doesn't take a release function?
  }
}
