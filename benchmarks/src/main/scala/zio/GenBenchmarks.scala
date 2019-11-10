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
  def zioDouble: List[Double] =
    unsafeRun(Gen.listOfN(size)(Gen.uniform).sample.map(_.value).runHead.get)

  @Benchmark
  def zioIntListsOfSizeN: List[List[Int]] =
    unsafeRun(Gen.listOfN(size)(Gen.listOfN(size)(Gen.anyInt)).sample.map(_.value).runHead.get)

  @Benchmark
  def zioStringsOfSizeN: List[String] =
    unsafeRun(Gen.listOfN(size)(Gen.stringN(size)(Gen.anyChar)).sample.map(_.value).runHead.get)

  @Benchmark
  def scalaCheckDoubles: List[Double] =
    scalacheck.Gen.listOfN(size, scalacheck.Gen.choose(0.0, 1.0)).sample.get

  @Benchmark
  def scalaCheckIntListsOfSizeN: List[List[Int]] =
    scalacheck.Gen.listOfN(size, scalacheck.Gen.listOfN(size, scalacheck.Gen.choose(Int.MinValue, Int.MaxValue))).sample.get

  @Benchmark
  def scalaCheckStringsOfSizeN: List[String] =
    scalacheck.Gen.listOfN(size, scalacheck.Gen.listOfN(size, scalacheck.Gen.alphaChar).sample.mkString).sample.get

  @Benchmark
  def hedgehogDoubles: List[Double] =
    hedgehog.Gen
      .list(hedgehog.Gen.double(hedgehog.Range.constant(0.0, 1.0)), hedgehog.Range.constant(0, size))
        .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
        .value
        ._2
        .head

  @Benchmark
  def hedgehogIntListsOfSizeN: List[List[Int]] = {
    val listRange = hedgehog.Range.constant(0, size)

    hedgehog.Gen
      .int(hedgehog.Range.constant(Int.MinValue, Int.MaxValue))
      .list(listRange)
      .list(listRange)
      .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
      .value
      ._2
      .head
  }

  @Benchmark
  def hedgehogStringsOfSizeN: List[String] =
    hedgehog.Gen
      .string(hedgehog.Gen.alpha, hedgehog.Range.constant(0, size))
      .list(hedgehog.Range.constant(0, size))
      .run(hedgehog.Size(0), hedgehog.core.Seed.fromTime())
      .value
      ._2
      .head

  @Benchmark
  def nyayaDoubles: List[Double] =
    nyaya.gen.Gen.double.list.sample

  @Benchmark
  def nyayaIntListsOfSizeN: List[List[Int]] =
    nyaya.gen.Gen.int.list(0 to size).list(0 to size).sample

  @Benchmark
  def nyayaStringsOfSizeN: List[String] =
    nyaya.gen.Gen.string(0 to size).list(0 to size).sample

}
