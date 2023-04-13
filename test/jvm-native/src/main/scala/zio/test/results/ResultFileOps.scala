package zio.test.results

import zio._

import java.io.IOException
import scala.io.Source

private[test] trait ResultFileOps {
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

private[test] object ResultFileOps {
  val live: ZLayer[Any, Nothing, ResultFileOps] =
    ZLayer.scoped(
      Json.apply
    )

  private[test] case class Json(resultPath: String, lock: Ref.Synchronized[Unit]) extends ResultFileOps {
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

    private def closeJson: ZIO[Scope, Throwable, Unit] =
      removeLastComma *>
        write("\n  ]\n}", append = true).orDie

    private def writeJsonPreamble: URIO[Any, Unit] =
      write(
        """|{
           |  "results": [""".stripMargin,
        append = false
      ).orDie

    private val removeLastComma =
      for {
        source <- ZIO.succeed(Source.fromFile(resultPath))
        updatedLines = {
          val lines = source.getLines().toList
          if (lines.nonEmpty && lines.last.endsWith(",")) {
            val lastLine    = lines.last
            val newLastLine = lastLine.dropRight(1)
            lines.init :+ newLastLine
          } else {
            lines
          }
        }
        _ <- ZIO.when(updatedLines.nonEmpty) {
               val firstLine :: rest = updatedLines
               for {
                 _ <- write(firstLine + "\n", append = false)
                 _ <- ZIO.foreach(rest)(line => write(line + "\n", append = true))
                 _ <- ZIO.addFinalizer(ZIO.attempt(source.close()).orDie)
               } yield ()
             }
      } yield ()

  }

  object Json {
    def apply: ZIO[Scope, Nothing, Json] =
      ZIO.acquireRelease(
        for {
          fileLock <- Ref.Synchronized.make[Unit](())
          instance  = Json("target/test-reports-zio/output.json", fileLock)
          _        <- instance.makeOutputDirectory.orDie
          _        <- instance.writeJsonPreamble
        } yield instance
      )(instance => instance.closeJson.orDie)
  }
}
