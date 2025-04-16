package zio.test.results

import zio._

import java.util.concurrent.ConcurrentLinkedQueue

private[test] trait ResultFileOps {
  def write(content: => String): UIO[Unit]
}

private[test] object ResultFileOps {
  val live: ZLayer[Any, Nothing, ResultFileOps] =
    ZLayer.scoped(Json.apply)

  private[test] final class Json private (resultPath: String) extends ResultFileOps {
    private val queue = new ConcurrentLinkedQueue[String]()

    def write(content: => String): UIO[Unit] =
      ZIO.succeed {
        queue.offer(content)
        ()
      }

    private def close: UIO[Unit] =
      ZIO.attemptBlocking {
        makeOutputDirectory()
        flushUnsafe()
      }.orDie

    private def makeOutputDirectory(): Unit = {
      import java.nio.file.{Files, Paths}

      val fp = Paths.get(resultPath)
      Files.createDirectories(fp.getParent)
      ()
    }

    private def flushUnsafe(): Unit = {
      val file = new java.io.FileWriter(resultPath, false)
      try {
        file.write("""|{
                      |  "results": [""".stripMargin)

        var next = queue.poll()
        while (next ne null) {
          val current = next
          next = queue.poll()
          if ((next eq null) && current.endsWith(",")) {
            file.write(current.dropRight(1))
          } else {
            file.write(current)
          }
        }
        file.write("\n  ]\n}")
      } finally {
        file.close()
      }
    }
  }

  object Json {
    def apply(filename: String): ZIO[Scope, Nothing, Json] =
      ZIO.acquireRelease(ZIO.succeed(new Json(filename)))(_.close)

    def apply: ZIO[Scope, Nothing, Json] =
      apply("target/test-reports-zio/output.json")
  }
}
