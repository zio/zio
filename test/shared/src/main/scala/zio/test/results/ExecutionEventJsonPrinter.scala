package zio.test.results

import zio.test._
import zio._

import java.io.IOException

object ExecutionEventJsonPrinter {
  val live = {
    implicit val trace = Trace.empty
    val instance       = new Live("target/test-reports-zio/output.json", Json)

    ZLayer.scoped(
      ZIO.acquireRelease(
        instance.resetContents("[") *>
          instance.makeOutputDirectory().orDie *>
          ZIO.succeed(instance)
      )(_ =>
        instance.removeTrailingComma() *>
          instance.closeJsonStructure()
      )
    )
  }

  class Live(resultPath: String, serializer: ResultSerializer) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = {
      implicit val trace = Trace.empty
      writeFile(resultPath, serializer.render(event)).orDie
    }

    def resetContents(newContents: String)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      ZIO
        .acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(resultPath, false)))(f =>
          ZIO.attemptBlocking(f.close()).orDie
        ) { f =>
          ZIO.attemptBlockingIO(f.write(newContents))
        }
        .orDie

    def closeJsonStructure()(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      writeFile(resultPath, "]").orDie

    def makeOutputDirectory()(implicit trace: Trace) = ZIO.attempt {
      import java.nio.file.{Files, Paths}

      val fp = Paths.get(resultPath)
      Files.createDirectories(fp.getParent)

    }

    import java.io._

    def removeTrailingComma()(implicit trace: Trace): ZIO[Any, Nothing, Unit] = ZIO.succeed {
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

  def writeFile(path: => String, content: => String)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(path, true)))(f =>
      ZIO.attemptBlocking(f.close()).orDie
    ) { f =>
      ZIO.attemptBlockingIO(f.append(content))
    }


}




