package zio.test

import org.openjdk.jmh.annotations._
import zio.BenchmarkUtil._
import zio.{Trace, ZIO}

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class GenBenchmark {

  @Param(Array("1000"))
  var size: Int = _

  @Param(Array("100"))
  var count: Long = _

  var listOfNEffect: ZIO[Any, Nothing, Unit] = _
  var causesEffect: ZIO[Any, Nothing, Unit]  = _

  @Setup
  def setup(): Unit = {
    listOfNEffect = Gen.listOfN(size)(Gen.byte).sample.forever.take(count).runDrain
    causesEffect = Sized.live(size)(Trace.tracer.newTrace) {
      Gen
        .causes(Gen.string, Gen.string.map(s => new RuntimeException(s)))
        .sample
        .forever
        .take(count)
        .runDrain
    }
  }

  @Benchmark
  def listOfN(): Unit =
    unsafeRun(listOfNEffect)

  @Benchmark
  def causes(): Unit =
    unsafeRun(causesEffect)
}
