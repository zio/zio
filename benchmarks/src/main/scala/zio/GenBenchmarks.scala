package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.scalacheck
import zio.test.Gen
import IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class GenBenchmarks {

  @Param(Array("1000"))
  var size: Int = _

  @Benchmark
  def zioDouble: Double =
    unsafeRun(Gen.uniform.sample.map(_.value).runHead.get)

  @Benchmark
  def zioIntListOfSizeN: List[Int] =
    unsafeRun(Gen.listOfN(size)(Gen.anyInt).sample.map(_.value).runHead.get)

  @Benchmark
  def zioStringOfSizeN: String =
    unsafeRun(Gen.stringN(size)(Gen.anyChar).sample.map(_.value).runHead.get)

  @Benchmark
  def scalaCheckDouble: Double =
    scalacheck.Gen.choose(0.0, 1.0).sample.get

  @Benchmark
  def scalaCheckIntListOfSizeN: List[Int] =
    scalacheck.Gen.listOfN(size, scalacheck.Gen.choose(Int.MinValue, Int.MaxValue)).sample.get

  @Benchmark
  def scalaCheckStringOfSizeN: String =
    scalacheck.Gen.listOfN(size, scalacheck.Gen.alphaChar).map(_.mkString).sample.get

  @Benchmark
  def hedgehogDouble: Double =
    hedgehog.Gen
      .double(hedgehog.Range.constant(0.0, 1.0))
      .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
      .value
      ._2
      .head

  @Benchmark
  def hedgehogIntListOfSizeN: List[Int] =
    hedgehog.Gen
      .int(hedgehog.Range.constant(Int.MinValue, Int.MaxValue))
      .list(hedgehog.Range.constant(0, size))
      .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
      .value
      ._2
      .head

  @Benchmark
  def hedgehogStringOfSizeN: String =
    hedgehog.Gen
      .string(hedgehog.Gen.alpha, hedgehog.Range.constant(0, size))
      .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
      .value
      ._2
      .head

  @Benchmark
  def nyayaDouble: Double =
    nyaya.gen.Gen.double.sample

  @Benchmark
  def nyayaIntListOfSizeN: List[Int] =
    nyaya.gen.Gen.int.list(0 to size).sample

  @Benchmark
  def nyayaStringOfSizeN: String =
    nyaya.gen.Gen.string(0 to size).sample

}
