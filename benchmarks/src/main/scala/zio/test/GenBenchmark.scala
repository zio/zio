package zio.test

import org.openjdk.jmh.annotations._
import zio.BenchmarkUtil._
import zio.{Random, ZIO}

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class GenBenchmark {

  @Param(Array("1000"))
  var size: Int = _

  @Param(Array("100"))
  var count: Long = _

  var listOfNEffect: ZIO[Random, Nothing, Unit] = _

  @Setup
  def setup(): Unit =
    listOfNEffect = Gen.listOfN(size)(Gen.byte).sample.forever.collectSome.take(count).runDrain

  @Benchmark
  def listOfN(): Unit =
    unsafeRun(listOfNEffect)
}
