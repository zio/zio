package zio.test.results

import zio._

import java.io.IOException

private[test] trait ResultFileOpsJson {
  // TODO Guarantee that file has been created by the time you are able to call this
  def write(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

private[test] object ResultFileOpsJson {
  val live: ZLayer[Any, Nothing, ResultFileOpsJson] = {
    val instance = Live("target/test-reports-zio/output.json")
    ZLayer.scoped(
      ZIO.acquireRelease(
        instance.makeOutputDirectory.orDie *>
          instance.writeJsonPreamble *>
          ZIO.succeed(instance)
      )(_ =>
        instance.closeJson.orDie
      )
    )
  }

 val test = {
    import java.nio.file.{Files}
    ZLayer(
      ZIO.attempt(
        Files.createTempFile("zio-test", ".json")
      ).map( path =>
        (path, Live(path.toString))
      )
    ).flatMap( tup => ZLayer.succeed(tup.get._1) ++ ZLayer.succeed(tup.get._2))
  }
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
