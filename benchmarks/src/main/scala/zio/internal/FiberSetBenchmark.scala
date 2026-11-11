package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@Threads(8)
class FiberSetBenchmark {
  var set: FiberSet                        = _
  var sampleFiber: FiberRuntime[Nothing, Unit] = _

  @Setup(Level.Iteration)
  def setup(): Unit = {
    set = FiberSet(1024)
    sampleFiber = Unsafe.unsafe { implicit u =>
      FiberRuntime[Nothing, Unit](FiberId.Gen.Live.make(Trace.empty), FiberRefs.empty, RuntimeFlags.default)
    }
  }

  @Benchmark
  def addAndRemove(): Boolean = {
    set.add(sampleFiber)
    set.remove(sampleFiber)
  }
}
