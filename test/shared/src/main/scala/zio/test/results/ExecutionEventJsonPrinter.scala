package zio.test.results

import zio.test._
import zio._

object ExecutionEventJsonPrinter {
  val live = {
    val x: ZIO[ResultFileOps with Scope, Nothing, Live] =
      for {
        resultFileOps <- ZIO.service[ResultFileOps]
        resultPath = "target/test-reports-zio/output.json"
        instance = new Live(resultPath, Json, resultFileOps)
        result <-
          ZIO.acquireRelease(
            resultFileOps.writeFile(resultPath, "[", append = false).orDie *>
              resultFileOps.makeOutputDirectory(resultPath).orDie *>
              ZIO.succeed(instance)
          )(_ =>
            resultFileOps.removeTrailingComma(resultPath) *>
              resultFileOps.writeFile(resultPath, "\n]", append = true).orDie
          )
      } yield  result
    ZLayer.scoped(x)
  }

  class Live(resultPath: String, serializer: ResultSerializer, resultFileOps: ResultFileOps) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = {
      resultFileOps.writeFile(resultPath, serializer.render(event), append = true).orDie
    }

  }
}




