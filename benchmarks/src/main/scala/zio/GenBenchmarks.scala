package zio

import java.util.concurrent.TimeUnit

import hedgehog.core.GenT
import org.openjdk.jmh.annotations._
import org.scalacheck
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

  @Benchmark
  def scalaCheckDouble: scalacheck.Gen[Double] =
    scalacheck.Gen.choose(0, size.toDouble)

  @Benchmark
  def scalaCheckIntListOfSizeN: scalacheck.Gen[List[Int]] =
    scalacheck.Gen.listOfN(size, scalacheck.Gen.choose(0, size))

  @Benchmark
  def scalaCheckStringOfSizeN: scalacheck.Gen[String] =
    scalacheck.Gen.listOfN(size, scalacheck.Gen.alphaChar).map(_.mkString)

  @Benchmark
  def hedgehogDouble: GenT[Double] =
    hedgehog.Gen.double(hedgehog.Range.constant(0, size))

  @Benchmark
  def hedgehogIntListOfSizeN: GenT[List[Int]] = {
    val range = hedgehog.Range.constant(0, size)
    hedgehog.Gen.int(range).list(range)
  }

  @Benchmark
  def hedgehogStringOfSizeN: GenT[String] =
    hedgehog.Gen.string(hedgehog.Gen.alpha, hedgehog.Range.constant(0, size))

  @Benchmark
  def nyayaDouble: nyaya.gen.Gen[Double] =
    nyaya.gen.Gen.double

  @Benchmark
  def nyayaIntListOfSizeN: nyaya.gen.Gen[List[Int]] =
    nyaya.gen.Gen.int.list(0 to size)

  @Benchmark
  def nyayaStringOfSizeN: nyaya.gen.Gen[String] =
    nyaya.gen.Gen.string(0 to size)

}
