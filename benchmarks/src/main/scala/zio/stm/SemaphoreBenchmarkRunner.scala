package zio.stm

import zio.ZIOAppDefault
import zio.ZIO
import org.openjdk.jmh.infra.Blackhole

object SemaphoreBenchmarkRunner extends ZIOAppDefault {
  def run =
    for {
      _        <- ZIO.succeed(println("Running benchmarks..."))
      benchmark = new SinglePermitSemaphoreBenchmark()
      _        <- ZIO.succeed(println("Running javaSemaphoreFair"))
      _        <- ZIO.succeed(benchmark.javaSemaphoreFair(new Blackhole("javaSemaphoreFair")))
      _        <- ZIO.succeed(println("Running javaSemaphoreUnfair"))
      _        <- ZIO.succeed(benchmark.javaSemaphoreUnfair(new Blackhole("javaSemaphoreUnfair")))
      _        <- ZIO.succeed(println("Running zioSemaphore"))
      _        <- ZIO.succeed(benchmark.zioSemaphore(new Blackhole("zioSemaphore")))
      _        <- ZIO.succeed(println("Running zioSemaphore3"))
      _        <- ZIO.succeed(new SinglePermitSemaphoreBenchmark().zioSemaphore3(new Blackhole("zioSemaphore3")))
      _        <- ZIO.succeed(println("Benchmarks finished."))
    } yield ()
}
