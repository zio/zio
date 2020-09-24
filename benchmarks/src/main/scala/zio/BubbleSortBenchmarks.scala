package zio

import java.util.concurrent.TimeUnit

import scala.collection.immutable.Range

import org.openjdk.jmh.annotations._

import zio.IOBenchmarks.unsafeRun

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class BubbleSortBenchmarks {
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

    unsafeRun(
      for {
        array <- IO.effectTotal[Array[Int]](createTestArray)
        _     <- bubbleSort[Int](_ <= _)(array)
        _     <- IO.effectTotal[Unit](assertSorted(array))
      } yield ()
    )
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
  @Benchmark
  def monixBubbleSort(): Unit = {
    import IOBenchmarks.monixScheduler
    import MonixIOArray._
    import monix.eval.Task

    (for {
      array <- Task.eval(createTestArray)
      _     <- bubbleSort[Int](_ <= _)(array)
      _     <- Task.eval(assertSorted(array))
    } yield ()).runSyncUnsafe(scala.concurrent.duration.Duration.Inf)
  }
}
