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
  def zioIntListOfSizeN: Gen[Random, List[Int]] =
    Gen.listOfN(size)(Gen.anyInt)

  @Benchmark
  def zioStringOfSizeN: Gen[Random, String] =
    Gen.stringN(size)(Gen.anyChar)

  @Benchmark
  def randomDouble: Double =
    scala.util.Random.nextDouble()

  @Benchmark
  def randomIntListOfSizeN: List[Int] =
    List.fill(size)(scala.util.Random.nextInt())

  @Benchmark
  def randomStringOfSizeN: String =
    scala.util.Random.alphanumeric.take(size).mkString
}
