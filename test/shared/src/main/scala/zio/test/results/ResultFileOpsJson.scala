package zio.test.results

import zio._

import java.io.IOException
import java.nio.file.Path

private[test] trait ResultFileOpsJson {
  // TODO Guarantee that file has been created by the time you are able to call this
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

private[test] object ResultFileOpsJson {
  val live: ZLayer[Any, Nothing, ResultFileOpsJson] =
    ZLayer.scoped(
      ZIO.acquireRelease(
        for {
          reentrantLockImposter <- Ref.Synchronized.make[Unit](())
          instance               = Live("target/test-reports-zio/output.json", reentrantLockImposter)
          _                     <- instance.makeOutputDirectory.orDie
          _                     <- instance.writeJsonPreamble
        } yield instance
      )(instance => instance.closeJson.orDie)
    )

  val test: ZLayer[Any, Throwable, Path with Live] =
    ZLayer.fromZIO {
      for {
        reentrantLockImposter <- Ref.Synchronized.make[Unit](())
        result <- ZIO
                    .attempt(
                      java.nio.file.Files.createTempFile("zio-test", ".json")
                    )
                    .map(path => (path, Live(path.toString, reentrantLockImposter)))
      } yield result
    }.flatMap(tup => ZLayer.succeed(tup.get._1) ++ ZLayer.succeed(tup.get._2))
}

private[test] case class Live(resultPath: String, lock: Ref.Synchronized[Unit]) extends ResultFileOpsJson {
  val makeOutputDirectory = ZIO.attempt {
    import java.nio.file.{Files, Paths}

    val fp = Paths.get(resultPath)
    Files.createDirectories(fp.getParent)
  }.unit

  def closeJson: ZIO[Any, Throwable, Unit] =
    removeTrailingComma *>
      write("\n  ]\n}", append = true).orDie

  def writeJsonPreamble: URIO[Any, Unit] =
    write(
      """|{
         |  "results": [""".stripMargin,
      append = false
    ).orDie

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

  import java.io._
  private val removeTrailingComma: ZIO[Any, Throwable, Unit] = ZIO.attempt {
    val file = new RandomAccessFile(resultPath, "rw")
    // Move the file pointer to the last character
    file.seek(file.length - 1)
    // Read backwards from the end of the file until we find a non-whitespace character
    var c = 0
    c = file.read
    while ({
      Character.isWhitespace(c)
    }) {
      file.seek(file.getFilePointer - 2)
      c = file.read
    }
    // If the non-whitespace character is a comma, remove it
    if (c == ',') file.setLength(file.length - 1)
    file.close()
  }

}
