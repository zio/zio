package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio.random.Random
import zio.test.Gen

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class GenBenchmarks {

  @Param(Array("1000"))
  var size: Int = _

  @Benchmark
  def zioDouble: Gen[Random, Double] =
    Gen.exponential

  @Benchmark
  def zioListOfInts: Gen[Random, List[Int]] =
    Gen.listOfN(size)(Gen.anyInt)

  @Benchmark
  def zioGenString: Gen[Random, String] =
    Gen.stringN(size)(Gen.anyChar)
}
