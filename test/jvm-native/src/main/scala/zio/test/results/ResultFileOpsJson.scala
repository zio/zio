package zio.test.results

import zio._

import java.io.IOException
import scala.io.Source

private[test] trait ResultFileOpsJson {
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

private[test] object ResultFileOpsJson {
  val live: ZLayer[Any, Nothing, ResultFileOpsJson] =
    ZLayer.scoped(
      Live.apply
    )
}

private[test] case class Live(resultPath: String, lock: Ref.Synchronized[Unit]) extends ResultFileOpsJson {
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

  private def closeJson: ZIO[Any, Throwable, Unit] =
    removeLastComma *>
      write("\n  ]\n}", append = true).orDie

  private def writeJsonPreamble: URIO[Any, Unit] =
    write(
      """|{
         |  "results": [""".stripMargin,
      append = false
    ).orDie

  import scala.util.Using

  private val removeLastComma =
    for {
      newLines <- ZIO.fromTry {
                    Using(Source.fromFile(resultPath)) { source =>
                      val lines = source.getLines().toList
                      if (lines.nonEmpty) {
                        val lastLine = lines.last
                        if (lastLine.endsWith(",")) {
                          val newLastLine = lastLine.dropRight(1)
                          lines.init :+ newLastLine
                        } else {
                          lines
                        }
                      } else {
                        lines
                      }
                    }
                  }
      firstLine :: rest = newLines
      _                <- write(firstLine + "\n", append = false)
      _                <- ZIO.foreach(rest)(line => write(line + "\n", append = true))
    } yield ()

}

object Live {
  def apply: ZIO[Scope, Nothing, Live] =
    ZIO.acquireRelease(
      for {
        fileLock <- Ref.Synchronized.make[Unit](())
        instance  = Live("target/test-reports-zio/output.json", fileLock)
        _        <- instance.makeOutputDirectory.orDie
        _        <- instance.writeJsonPreamble
      } yield instance
    )(instance => instance.closeJson.orDie)
}
