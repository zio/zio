package zio.test.results

import zio.test._
import zio._

/**
 * Determines test results are written for later analysis. TODO Figure out what
 * happens when we are cross building. I'm worried the artifacts get
 * overwritten.
 */
private[test] object ExecutionEventJsonPrinter {
  val live: ZLayer[ResultSerializer with ResultFileOpsJson, Nothing, Live] =
    ZLayer.fromZIO(
      for {
        token <- System.env("ZIO_TEST_GITHUB_TOKEN").orDie // TODO Should we die here?
        inCi = token.isDefined
        impl <-
          if (inCi) {
            for {
              _ <- ZIO.debug("Running in CI. Write test results to file.")
              serializer <- ZIO.service[ResultSerializer]
              fileOps <- ZIO.service[ResultFileOpsJson]
            } yield LiveImpl(serializer, fileOps)
          } else {
            ZIO.debug("Not running in CI. Do not write test results to file.") *>
              ZIO.succeed(NoOp)
          }
      } yield impl
    )

  object NoOp extends Live {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] = ZIO.unit
  }

  trait Live extends ExecutionEventPrinter

  case class LiveImpl(serializer: ResultSerializer, resultFileOps: ResultFileOpsJson) extends Live {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      resultFileOps.write(serializer.render(event), append = true).orDie
  }
}
