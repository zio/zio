package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.annotations.Warmup

import zio.IOBenchmarks.verify

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class FiberRefBenchmarks {
  @Param(Array("32"))
  var n: Int = _

  @Benchmark
  def tracedCreateUpdateAndRead(): Unit =
    createUpdateAndRead(IOBenchmarks.TracedRuntime)

  @Benchmark
  def unTracedCreateUpdateAndRead(): Unit =
    createUpdateAndRead(IOBenchmarks)

  @Benchmark
  def unTracedJustYield(): Unit =
    justYield(IOBenchmarks)

  @Benchmark
  def unTracedCreateFiberRefsAndYield(): Unit =
    createFiberRefsAndYield(IOBenchmarks)

  private def justYield(runtime: Runtime[Any]) = runtime.unsafeRun {
    for {
      _ <- ZIO.foreach_(1.to(n))(_ => ZIO.yieldNow)
    } yield ()
  }

  private def createFiberRefsAndYield(runtime: Runtime[Any]) = runtime.unsafeRun {
    for {
      fiberRefs <- ZIO.foreach(1.to(n))(i => FiberRef.make(i))
      _         <- ZIO.foreach_(1.to(n))(_ => ZIO.yieldNow)
      values    <- ZIO.foreachPar(fiberRefs)(_.get)
      _         <- verify(values == 1.to(n))(s"Got $values")
    } yield ()
  }

  private def createUpdateAndRead(runtime: Runtime[Any]) = runtime.unsafeRun {
    for {
      fiberRefs <- ZIO.foreach(1.to(n))(i => FiberRef.make(i))
      values1   <- ZIO.foreachPar(fiberRefs)(ref => ref.update(-_) *> ref.get)
      values2   <- ZIO.foreachPar(fiberRefs)(_.get)
      _ <- verify(values1.forall(_ < 0) && values1.size == values2.size)(
            s"Got \nvalues1: $values1, \nvalues2: $values2"
          )
    } yield ()
  }
}
