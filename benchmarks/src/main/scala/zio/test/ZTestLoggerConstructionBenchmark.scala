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
class ZTestLoggerConstructionBenchmark {

  @Param(Array("10000"))
  var loggerCount: Int = _

  private implicit val trace: Trace = Trace.empty

  private var construct: UIO[Unit] = _

  @Setup
  def setup(): Unit =
    construct = ZIO.loopDiscard(0)(_ < loggerCount, _ + 1)(_ => ZTestLogger.locally(ZIO.unit))

  @Benchmark
  def constructLoggers(): Unit =
    unsafeRun(construct)
}
