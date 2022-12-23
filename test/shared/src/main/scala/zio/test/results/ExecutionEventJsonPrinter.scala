package zio.test.results

import zio.test.{
  ExecutionEvent,
  ExecutionEventPrinter,
  TestAnnotationMap,
  TestAnnotationRenderer,
  TestFailure,
  TestSuccess
}
import zio.{Scope, Trace, ZIO, ZLayer, ZOutputStream}

import java.io
import java.io.IOException
import java.nio.file.Path

object ExecutionEventJsonPrinter {
  val live = {
    implicit val trace = Trace.empty
    val instance       = new Live()

    ZLayer.scoped(
      ZIO.acquireRelease(
        instance.reset("target/test-reports-zio/output.json") *>
          instance.makeOutputDirectory().orDie *>
          ZIO.succeed(instance)
      )(_ =>
        instance.removeTrailingComma("target/test-reports-zio/output.json") *>
          instance.closeJsonStructure("target/test-reports-zio/output.json") *>
          ZIO.debug("Should remove last command and add closing square bracket")
      )
    )
  }

  class Live() extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = {
      implicit val trace = Trace.empty
      writeFile("target/test-reports-zio/output.json", jsonify(event)).orDie
    }

    private def jsonify[E](test: Either[TestFailure[E], TestSuccess]): String =
      test match {
        case Left(value) =>
          "Failure"
        case Right(value) =>
          "Success"
      }

    private def jsonify(testAnnotationMap: TestAnnotationMap): String =
      TestAnnotationRenderer.default
        .run(List.empty, testAnnotationMap)
        .map(s => s.replace("\"", "\\\""))
        .mkString(" : ")

    private def jsonify(executionEvent: ExecutionEvent): String = executionEvent match {
      case ExecutionEvent.Test(labelsReversed, test, annotations, ancestors, duration, id, fullyQualifiedName) =>
        s"""
           |  {
           |     "name" : "$fullyQualifiedName/${labelsReversed.reverse.mkString("/")}",
           |     "status" : "${jsonify(test)}",
           |     "durationMillis" : "${duration}",
           |     "annotations" : "${jsonify(annotations)}"
           |  },""".stripMargin
      case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) =>
        ""
      case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors) =>
        // TODO Deal with trailing commas
        ""
      case ExecutionEvent.TopLevelFlush(id) => "TODO TopLevelFlush"
      case ExecutionEvent.RuntimeFailure(id, labelsReversed, failure, ancestors) =>
        "TODO RuntimeFailure"
    }

    def reset(path: => String)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      ZIO
        .acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(path, false)))(f =>
          ZIO.attemptBlocking(f.close()).orDie
        ) { f =>
          ZIO.attemptBlockingIO(f.write("["))
        }
        .orDie

    def closeJsonStructure(path: => String)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      writeFile(path, "]").orDie

    def makeOutputDirectory()(implicit trace: Trace) = ZIO.attempt {
      import java.nio.file.{Files, Paths}

      val fp = Paths.get("target/test-reports-zio/newfile.txt")
      Files.createDirectories(fp.getParent)
//      Files.createFile(fp)

    }

    import java.io._

    def removeTrailingComma(filePath: String)(implicit trace: Trace): ZIO[Any, Nothing, Unit] = ZIO.succeed {
      try {
        val file = new RandomAccessFile(filePath, "rw")
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

  def writeFile(path: => Path, content: => String)(implicit
    trace: Trace,
    d: DummyImplicit
  ): ZIO[Any, IOException, Unit] =
    writeFile(path.toString, content)

  def writeFileOutputStream(
    path: => String
  )(implicit trace: Trace): ZIO[Scope, IOException, ZOutputStream] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlockingIO {
          val fos = new io.FileOutputStream(path)
          (fos, ZOutputStream.fromOutputStream(fos))
        }
      )(tuple => ZIO.attemptBlocking(tuple._1.close()).orDie)
      .map(_._2)

  def writeFileOutputStream(
    path: => Path
  )(implicit trace: Trace, d: DummyImplicit): ZIO[Scope, IOException, ZOutputStream] =
    writeFileOutputStream(path.toString)

}
