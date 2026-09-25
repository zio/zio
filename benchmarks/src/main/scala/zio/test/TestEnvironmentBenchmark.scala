package zio.test

import org.openjdk.jmh.annotations.{Scope => JmhScope, _}
import zio.BenchmarkUtil.unsafeRun
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.TimeUnit

@State(JmhScope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
class TestEnvironmentBenchmark {

  @Param(Array("100", "1000", "10000"))
  var environmentCount: Int = _

  private var acquireAndRelease: UIO[Unit] = _

  @Setup
  def setup(): Unit = {
    implicit val trace: Trace = Trace.empty

    acquireAndRelease = ZIO.loopDiscard(0)(_ < environmentCount, _ + 1)(_ => ZIO.unit.provideLayer(testEnvironment))
  }

  @Benchmark
  def construct(): Unit =
    unsafeRun(acquireAndRelease)
}
