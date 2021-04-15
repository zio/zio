package zio

import org.openjdk.jmh.annotations.{
  Benchmark,
  BenchmarkMode,
  Fork,
  Level,
  Measurement,
  Mode,
  OutputTimeUnit,
  Param,
  Scope,
  Setup,
  State,
  Warmup
}

import java.lang.{System => JSystem}
import java.util.concurrent.TimeUnit
import scala.collection.Iterable

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Measurement(iterations = 2, timeUnit = TimeUnit.SECONDS, time = 5)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 5)
class ParallelMergeSortBenchmark {

  @Param(Array("10000"))
  var size: Int = _

  @Param(Array("128"))
  var samples: Int = _

  @Param(Array("4096"))
  var parThreshold: Int = _

  private var sortInput: List[Vector[Int]] = _

  @Setup(Level.Iteration)
  def setup(): Unit =
    sortInput = 1
      .to(samples)
      .map(_ => scala.util.Random.shuffle(1.to(size).toVector))
      .toList

  @Benchmark
  def zioSort(): Unit = benchMergeSort(IOBenchmarks)

  @Benchmark
  def zioSortTraced(): Unit = benchMergeSort(IOBenchmarks.TracedRuntime)

  @Benchmark
  def scalaCollectionSort(): Unit = {
    val sortOutput = sortInput.map(_.sorted)
    sortInput.zip(sortOutput).foreach(verifySorted)
  }

  private def benchMergeSort(runtime: Runtime[Any]): Unit = runtime.unsafeRun {
    for {
      sortOutput <- ZIO.foreach(sortInput)(mergeSort)
      _          <- ZIO.foreach(sortInput.zip(sortOutput))(verifySorted)
    } yield ()
  }

  private def verifySorted(inOut: (Iterable[Int], Iterable[Int])): IO[AssertionError, Unit] = {
    val sorted = inOut._2.toArray.sliding(2).forall {
      case Array(i1, i2) => i1 <= i2
      case _             => true
    }

    IOBenchmarks.verify(sorted)(s"Not sorted: ${inOut._2} <-- ${inOut._1}")
  }

  private def mergeSort(is: Iterable[Int]): UIO[Iterable[Int]] =
    for {
      array <- UIO(is.toArray)
      buf   <- UIO(new Array[Int](array.length / 2))
      _     <- mergeSortInPlace(array, buf, 0, array.length)
    } yield array.toIterable

  private def mergeSortInPlace(is: Array[Int], buf: Array[Int], start: Int, end: Int): UIO[Unit] = {
    val len = end - start
    if (len < 2) IO.unit
    else if (len == 2) {
      if (is(start) <= is(start + 1)) IO.unit
      else UIO(swap(is, start, start + 1))
    } else {
      val middle    = start + len / 2
      val leftSort  = mergeSortInPlace(is, buf, start, middle)
      val rightSort = mergeSortInPlace(is, buf, middle, end)

      val sortParts =
        if (len >= parThreshold) leftSort.zipParLeft(rightSort)
        else leftSort.zipLeft(rightSort)

      sortParts *> mergeInPlace(is, buf, start, middle, end)
    }
  }

  private def mergeInPlace(is: Array[Int], buf: Array[Int], start: Int, middle: Int, end: Int): UIO[Unit] = UIO {
    var i  = start / 2
    val ie = i + middle - start
    var j  = middle
    var k  = start
    JSystem.arraycopy(is, start, buf, i, middle - start)

    while (i < ie && j < end) {
      val (a, b) = (buf(i), is(j))
      if (a < b) {
        is(k) = a
        i += 1
      } else {
        is(k) = b
        j += 1
      }

      k += 1
    }

    if (i < ie) {
      JSystem.arraycopy(buf, i, is, k, ie - i)
    }
  }

  private def swap(is: Array[Int], i: Int, j: Int): Unit = {
    val tmp = is(i)
    is(i) = is(j)
    is(j) = tmp
  }
}
