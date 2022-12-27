package zio.test.results

import zio._

import java.io.IOException

private[test] trait ResultFileOpsJson {
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

private[test] object ResultFileOpsJson {
  object Live extends Live("target/test-reports-zio/output.json")
  val live: ZLayer[Any, Nothing, ResultFileOpsJson] =
    ZLayer.scoped(
      ZIO.acquireRelease(
        Live.makeOutputDirectory.orDie *>
        Live.writeJsonPreamble *>
          ZIO.succeed(Live)
        )(_ =>
          Live.closeJson.orDie
        )
    )

  }

  private[test] case class Live(resultPath: String) extends ResultFileOpsJson {
    val makeOutputDirectory = ZIO.attempt {
      import java.nio.file.{Files, Paths}

      val fp = Paths.get(resultPath)
      Files.createDirectories(fp.getParent)
    }.unit

    def closeJson: ZIO[Any, Throwable, Unit] =
      removeTrailingComma *>
      write("\n  ]\n}", append = true).orDie

    def writeJsonPreamble: URIO[Any, Unit] = {
      write(
        """|{
           |  "results": [""".stripMargin, append = false).orDie
    }

    def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit] =
      ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(resultPath, append)))(f =>
        ZIO.attemptBlocking(f.close()).orDie
      ) { f =>
        ZIO.attemptBlockingIO(f.append(content))
      }

    import java.io._
    private val removeTrailingComma: ZIO[Any, Throwable, Unit] = ZIO.attempt {
      val file = new RandomAccessFile(resultPath, "rw")
      // Move the file pointer to the last character
      file.seek(file.length - 1)
      // Read backwards from the end of the file until we find a non-whitespace character
      var c = 0
      do {
        c = file.read
        file.seek(file.getFilePointer - 2)
      } while ({
        Character.isWhitespace(c)
      })
      // If the non-whitespace character is a comma, remove it
      if (c == ',') file.setLength(file.length - 1)
      file.close()
    }

  }
