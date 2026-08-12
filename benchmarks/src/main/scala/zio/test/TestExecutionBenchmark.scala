package zio.test

import org.openjdk.jmh.annotations.{Scope => JmhScope, _}
import zio.BenchmarkUtil.unsafeRun
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.TimeUnit
import scala.annotation.nowarn

@State(JmhScope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
class TestExecutionBenchmark {

  @Param(Array("100", "1000", "10000"))
  var leafCount: Int = _

  @Param(Array("baseline", "optimized"))
  var implementation: String = _

  private var execution: UIO[Summary] = _

  @Setup
  def setup(): Unit = {
    implicit val trace: Trace = Trace.empty

    val leaves =
      Chunk.fromArray(
        Array.tabulate(leafCount) { index =>
          Spec.labeled(
            s"leaf-$index",
            Spec.test(ZIO.succeed(TestSuccess.Succeeded()), TestAnnotationMap.empty)
          )
        }
      )
    val spec = TestAspect.fibers(
      TestAspect.timeoutWarning(60.seconds)(Spec.labeled("trivial", Spec.multiple(leaves)))
    ): @nowarn("cat=deprecation")
    val freshLayer =
      if (implementation == "baseline") {
        val sizedLive  = Sized.live(100)(Trace.empty)
        val configLive = TestConfig.live(100, 100, 200, 1000)(Trace.empty)
        val testFiberRefGen =
          ZLayer.scoped(FiberRef.currentFiberIdGenerator.locallyScoped(FiberId.Gen.Monotonic))
        val baselineTestEnvironment =
          liveEnvironment >>> (
            Annotations.live <*>
              Live.default <*>
              sizedLive <*>
              ((Live.default <*> Annotations.live) >>> TestClock.default) <*>
              configLive <*>
              ((Live.default <*> Annotations.live) >>> TestConsole.debug) <*>
              TestRandom.deterministic <*>
              TestSystem.default <*>
              testFiberRefGen
          )
        baselineTestEnvironment
      } else testEnvironment
    val executor =
      TestExecutor.default[Any, Nothing](
        ZLayer.empty,
        freshLayer <*> Scope.default,
        ZLayer.fromZIO(
          ExecutionEventSink.ExecutionEventSinkLive(new TestOutput {
            def print(executionEvent: ExecutionEvent): UIO[Unit] = ZIO.unit
          })
        ),
        ZTestEventHandler.silent
      )

    execution = executor.run("zio.test.TestExecutionBenchmark", spec, ExecutionStrategy.Sequential)
  }

  @Benchmark
  def runTrivialLeaves(): Unit =
    unsafeRun(execution)
}
