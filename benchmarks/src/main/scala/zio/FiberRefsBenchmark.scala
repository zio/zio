package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class FiberRefsBenchmark {
  implicit val us: Unsafe = Unsafe.unsafe

  def commonList = List.fill(10)("foo") :+ scala.util.Random.alphanumeric.take(10).mkString
  val ref        = FiberRef.unsafe.make(commonList)

  val id1 = FiberId.Runtime(0, 0, Trace.empty)
  val id2 = FiberId.Runtime(0, 1, Trace.empty)

  val f1 = FiberRefs.empty.updatedAs(id1)(ref, commonList)
  val f2 = f1.forkAs(id2).updatedAs(id2)(ref, commonList)

  val list = commonList

  @Benchmark
  def updateRef() =
    f1.updatedAs(id1)(ref, list)

  @Benchmark
  def joinRef() =
    f1.joinAs(id1)(f2)

  @Benchmark
  def forkRef() =
    // To prevent optimizations in forkAs we recreate the FiberRefs
    // This benchmark is mostly for optimizing the forkAs method after the FiberRefs are modified
    FiberRefs(f1.fiberRefLocals).forkAs(id2)

}
