package zio.test

import org.openjdk.jmh.annotations.{Scope => JmhScope, _}
import zio.BenchmarkUtil.unsafeRun
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import scala.collection.immutable.SortedSet

@State(JmhScope.Benchmark)
@BenchmarkMode(Array(Mode.SingleShotTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(3)
class TestFiberSupervisorBenchmark {

  @Param(Array("10000"))
  var leafCount: Int = _

  private implicit val trace: Trace = Trace.empty

  private var previous: UIO[Unit] = _
  private var current: UIO[Unit]  = _

  @Setup
  def setup(): Unit = {
    previous = ZIO.loopDiscard(0)(_ < leafCount, _ + 1) { _ =>
      ZIO.suspendSucceed {
        val ref = new AtomicReference(SortedSet.empty[Fiber.Runtime[Any, Any]])
        Supervisor.fibersIn(ref).flatMap(ZIO.unit.supervised(_))
      }
    }
    current = ZIO.loopDiscard(0)(_ < leafCount, _ + 1) { _ =>
      ZIO.suspendSucceed {
        val ref = new AtomicReference(SortedSet.empty[Fiber.Runtime[Any, Any]])
        ZIO.unit.supervised(Supervisor.unsafe.fibersIn(ref))
      }
    }
  }

  @Benchmark
  def effectfulConstruction(): Unit =
    unsafeRun(previous)

  @Benchmark
  def directConstruction(): Unit =
    unsafeRun(current)
}
