package zio

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class SinglePermitSemaphoreBenchmark {

  var semaphore: Semaphore = _

  @Setup
  def setup(): Unit =
    semaphore = Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(Semaphore.make(1)).getOrThrow())

  @Benchmark
  def zioSemaphore(): Unit =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(semaphore.withPermit(ZIO.unit)).getOrThrow()
    }
}
