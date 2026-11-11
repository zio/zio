package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
class SemaphoreBenchmark {
  @Param(Array("1", "2", "3", "5", "10"))
  var threads: Int = 1

  @Param(Array("1", "4"))
  var permits: Long = 1L

  val n: Int = 1000

  @Benchmark
  def withPermitContention(): Unit =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe
        .run(
          for {
            sem <- Semaphore.make(permits)
            fiber <- ZIO.foreachParDiscard(1 to threads) { _ =>
                       ZIO.replicateZIODiscard(n / threads) {
                         sem.withPermit(ZIO.unit)
                       }
                     }.fork
            _ <- fiber.join
          } yield ()
        )
        .getOrThrowFiberFailure()
    }
}
