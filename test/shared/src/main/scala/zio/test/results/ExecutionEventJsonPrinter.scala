package zio.test.results

import zio.test._
import zio._

object ExecutionEventJsonPrinter {
  val live = {
    implicit val trace = Trace.empty

    val x: ZIO[ResultFileOps with Scope, Nothing, Live] =
      for {
        resultFileOps <- ZIO.service[ResultFileOps]
//        x = resultFileOps.get
        instance = new Live("target/test-reports-zio/output.json", Json, resultFileOps)
        result <-
          ZIO.acquireRelease(
            instance.resetContents("[") *>
              resultFileOps.makeOutputDirectory("target/test-reports-zio/output.json").orDie *>
              ZIO.succeed(instance)
          )(_ =>
            resultFileOps.removeTrailingComma("target/test-reports-zio/output.json") *>
              instance.closeJsonStructure()
          )
      } yield  result
    ZLayer.scoped(x)
  }

  class Live(resultPath: String, serializer: ResultSerializer, resultFileOps: ResultFileOps) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = {
      implicit val trace = Trace.empty
      resultFileOps.writeFile(resultPath, serializer.render(event), append = true).orDie
    }

    def resetContents(newContents: String)(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      resultFileOps.writeFile(resultPath, newContents, append = false).orDie

    def closeJsonStructure()(implicit trace: Trace): ZIO[Any, Nothing, Unit] =
      resultFileOps.writeFile(resultPath, "\n]", append = true).orDie

  }


}




