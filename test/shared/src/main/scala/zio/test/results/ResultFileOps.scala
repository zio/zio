package zio.test.results

import zio._

import java.io.IOException

trait ResultFileOps {
  def makeOutputDirectory(resultPath: String)(implicit trace: Trace): Task[Unit]
  def writeFile(path: => String, content: => String, append: Boolean)(implicit
    trace: Trace
  ): ZIO[Any, IOException, Unit]
  def removeTrailingComma(resultPath: String)(implicit trace: Trace): ZIO[Any, Nothing, Unit]
}

object ResultFileOps {
  val live = ZLayer.succeed(Live())
  case class Live() extends ResultFileOps {
    def makeOutputDirectory(resultPath: String)(implicit trace: Trace) = ZIO.attempt {
      import java.nio.file.{Files, Paths}

      val fp = Paths.get(resultPath)
      Files.createDirectories(fp.getParent)
    }.unit

    def writeFile(path: => String, content: => String, append: Boolean)(implicit
      trace: Trace
    ): ZIO[Any, IOException, Unit] =
      ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(path, append)))(f =>
        ZIO.attemptBlocking(f.close()).orDie
      ) { f =>
        ZIO.attemptBlockingIO(f.append(content))
      }

    import java.io._
    def removeTrailingComma(resultPath: String)(implicit trace: Trace): ZIO[Any, Nothing, Unit] = ZIO.succeed {
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
