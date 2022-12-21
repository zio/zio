package zio.test

import zio.interop.javaz
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio._

import java.io
import java.io.IOException
import java.net.{URI, URL}
import java.nio.channels.CompletionHandler
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.{CompletableFuture, CompletionStage, Future}

object ExecutionEventJsonPrinter {
  val live = {
    implicit val trace = Trace.empty
    val instance = new Live()

    for {
      _ <- ZLayer.fromZIO(instance.clearFile("output.json").orDie)
    } yield ZEnvironment(instance)
  }

  class Live() extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = {
      implicit val trace = Trace.empty
      event match {
        case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors) =>
          println("Attempting to remove comma")
          removeTrailingComma("output.json")

        case _ => ()
      }
      writeFile("output.json", jsonify(event)).orDie
    }

    private def jsonify[E](test: Either[TestFailure[E], TestSuccess]): String = test match {
      // TODO Render annotations
      case Left(value) =>
        "Failure"
      case Right(value) =>
        "Success"
    }

    private def jsonify(executionEvent: ExecutionEvent): String = executionEvent match {
      case ExecutionEvent.Test(labelsReversed, test, annotations, ancestors, duration, id) =>
        s"""
          | {
          |    "testName" : "${labelsReversed.head}",
          |    "testStatus" : "${jsonify(test)}",
          |    "durationMillis" : "${duration}"
          | },""".stripMargin
      case ExecutionEvent.SectionStart(labelsReversed, id, ancestors) =>
        s"""{
           |   "suiteName" : "${labelsReversed.head}",
           |   "children" : [
           |""".stripMargin
      case ExecutionEvent.SectionEnd(labelsReversed, id, ancestors) =>
        // TODO Deal with trailing commas
        s"""
          |   ]
          |}${if(ancestors.head != SuiteId.global) "," else ""}""".stripMargin
      case ExecutionEvent.TopLevelFlush(id) => "TODO TopLevelFlush"
      case ExecutionEvent.RuntimeFailure(id, labelsReversed, failure, ancestors) =>
        "TODO RuntimeFailure"
    }

    def clearFile(path: => String)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
      ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(path, false)))(f =>
        ZIO.attemptBlocking(f.close()).orDie
      ) { f =>
        ZIO.attemptBlockingIO(f.write(""))
      }

    import java.io._


    def removeTrailingComma(filePath: String): Unit = {
      try {
        val file = new RandomAccessFile(filePath, "rw")
        // Move the file pointer to the last character
        file.seek(file.length - 1)
        // Read backwards from the end of the file until we find a non-whitespace character
        var c = 0
        do {
          c = file.read
          file.seek(file.getFilePointer - 2)
        } while ( {
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
      ZIO.debug("Should write this to a file: " + content) <*
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
