package zio

import zio.test._
import zio.test.Assertion._
import zio.stm.SinglePermitSemaphoreBenchmark
import org.openjdk.jmh.infra.Blackhole

object SemaphoreBenchmarkSpec extends ZIOSpecDefault {
  def spec = suite("SemaphoreBenchmarkSpec")(
    test("run benchmark") {
      val benchmark = new zio.stm.SinglePermitSemaphoreBenchmark()
      benchmark.fibers = 10
      benchmark.permits = 5
      for {
        _ <- ZIO.succeed(println("Running javaSemaphoreFair"))
        _ <- ZIO.succeed(benchmark.javaSemaphoreFair(new Blackhole()))
        _ <- ZIO.succeed(println("Running javaSemaphoreUnfair"))
        _ <- ZIO.succeed(benchmark.javaSemaphoreUnfair(new Blackhole()))
        _ <- ZIO.succeed(println("Running zioSemaphore"))
        _ <- ZIO.succeed(benchmark.zioSemaphore(new Blackhole()))
        _ <- ZIO.succeed(println("Running zioSemaphore3"))
        _ <- ZIO.succeed(benchmark.zioSemaphore3(new Blackhole()))
      } yield assert(true)(isTrue)
    }
  )
}
