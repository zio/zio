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
      writeFile("output.json", event.toString).orDie
    }

    def clearFile(path: => String)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
      ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(path, false)))(f =>
        ZIO.attemptBlocking(f.close()).orDie
      ) { f =>
        ZIO.attemptBlockingIO(f.write(""))
      }

  }

  def writeFile(path: => String, content: => String)(implicit trace: Trace): ZIO[Any, IOException, Unit] =
    ZIO.acquireReleaseWith(ZIO.attemptBlockingIO(new java.io.FileWriter(path, true)))(f =>
      ZIO.attemptBlocking(f.close()).orDie
    ) { f =>
      ZIO.debug("Should write this to a file: " + content) <*
      ZIO.attemptBlockingIO(f.append(content + "\n"))
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
