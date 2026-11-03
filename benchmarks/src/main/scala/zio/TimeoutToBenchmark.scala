package zio

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class TimeoutToBenchmark {
  @Param(Array("1000"))
  var n: Int = _

  @Benchmark
  def zioBaseline(): Int =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(ZIO.succeed(42).repeatN(n)).getOrThrowFiberFailure()
    }

  @Benchmark
  def zioTimeoutToFastPath(): Int = {
    val step = ZIO.succeed(42).timeoutTo(0)(identity)(10.seconds)
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(step.repeatN(n)).getOrThrowFiberFailure()
    }
  }
}
