package zio.test.results

import zio._

import java.io.IOException

trait ResultFileOpsJson {
  def writeFile(content: => String, append: Boolean): ZIO[Any, IOException, Unit]
}

object ResultFileOpsJson {
  val opsLive = Live("target/test-reports-zio/output.json")
  val live: ZLayer[Any, Nothing, ResultFileOpsJson] = {
    ZLayer.scoped(
      ZIO.acquireRelease(
      opsLive.writeFile("[", append = false).orDie *>
        opsLive.makeOutputDirectory.orDie *>
        ZIO.succeed(opsLive)
      )(_ =>
      opsLive.removeTrailingComma *>
        opsLive.writeFile("\n]", append = true).orDie
      )
    )
  }

  case class Live private(resultPath: String) extends ResultFileOpsJson {
    val makeOutputDirectory = ZIO.attempt {
      import java.nio.file.{Files, Paths}

      val fp = Paths.get(resultPath)
      Files.createDirectories(fp.getParent)
    }.unit

    def writeFile(content: => String, append: Boolean): ZIO[Any, IOException, Unit] =
      ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(resultPath, append)))(f =>
        ZIO.attemptBlocking(f.close()).orDie
      ) { f =>
        ZIO.attemptBlockingIO(f.append(content))
      }

    import java.io._
    val removeTrailingComma: ZIO[Any, Nothing, Unit] = ZIO.succeed {
      try {
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
      } catch {
        case e: IOException =>
          e.printStackTrace()
      }
    }
  }

}
