package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil.unsafeRun

import java.util.concurrent.TimeUnit
import scala.collection.immutable.Range

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class BubbleSortBenchmark {
  @Param(Array("1000"))
  var size: Int = _

  def createTestArray: Array[Int] = Range.inclusive(1, size).toArray.reverse
  def assertSorted(array: Array[Int]): Unit =
    if (!array.sorted.sameElements(array)) {
      throw new Exception("Array not correctly sorted")
    }

  @Benchmark
  def zioBubbleSort(): Unit = {
    import ZIOArray._

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(
        for {
          array <- ZIO.succeed[Array[Int]](createTestArray)
          _     <- bubbleSort[Int](_ <= _)(array)
          _     <- ZIO.succeed[Unit](assertSorted(array))
        } yield ()
      )
    }
  }
  @Benchmark
  def catsBubbleSort(): Unit = {
    import CatsIOArray._
    import cats.effect.IO

    (for {
      array <- IO(createTestArray)
      _     <- bubbleSort[Int](_ <= _)(array)
      _     <- IO(assertSorted(array))
    } yield ()).unsafeRunSync()
  }
}
