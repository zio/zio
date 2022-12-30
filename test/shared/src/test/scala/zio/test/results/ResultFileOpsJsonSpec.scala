package zio.test.results

import zio._
import zio.test._

object ResultFileOpsJsonSpec extends ZIOSpecDefault {
  def spec = test("clobbered concurrent writes"){
    for {
      _ <-
        ZIO.service[ResultFileOpsJson].flatMap(_.write("a", append = true))
    } yield assertCompletes
  }.provide(ExecutionEventJsonPrinter.live)
}
