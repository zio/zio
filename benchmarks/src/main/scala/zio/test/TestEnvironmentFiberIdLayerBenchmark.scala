package zio.test

import org.openjdk.jmh.annotations.{Scope => JmhScope, _}
import zio.BenchmarkUtil.unsafeRun
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.TimeUnit

@State(JmhScope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(3)
class TestEnvironmentFiberIdLayerBenchmark {

  @Param(Array("10000"))
  var environmentCount: Int = _

  private implicit val trace: Trace = Trace.empty

  private var separate: UIO[Unit] = _
  private var fused: UIO[Unit]    = _

  @Setup
  def setup(): Unit = {
    val fiberIdGenerator =
      ZLayer.scoped(FiberRef.currentFiberIdGenerator.locallyScoped(FiberId.Gen.Monotonic))
    val separateLayer = liveEnvironment >>> (TestEnvironment.live <*> fiberIdGenerator)

    separate = ZIO.loopDiscard(0)(_ < environmentCount, _ + 1)(_ => ZIO.unit.provideLayer(separateLayer))
    fused = ZIO.loopDiscard(0)(_ < environmentCount, _ + 1)(_ => ZIO.unit.provideLayer(testEnvironment))
  }

  @Benchmark
  def separateFiberIdLayer(): Unit =
    unsafeRun(separate)

  @Benchmark
  def fusedFiberIdInstallation(): Unit =
    unsafeRun(fused)
}
