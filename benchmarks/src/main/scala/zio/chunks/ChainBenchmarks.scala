package zio.chunks

import java.util.concurrent.TimeUnit

import cats.data.Chain
import org.openjdk.jmh.annotations._
import zio.Chunk

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
class ChainBenchmarks {

  val largeChain: Chain[Int] =
    (0 to 1000).foldLeft(Chain.empty[Int])((acc, _) => acc ++ Chain.fromSeq(0 to 1000))

  val largeChunk: Chunk[Int] =
    (0 to 1000).foldLeft[Chunk[Int]](Chunk.empty)((acc, _) => acc ++ Chunk.fromIterable(0 to 1000))

  val largeVector: Vector[Int] =
    (0 to 1000000).toVector

  val largeList: List[Int] =
    (0 to 1000000).toList

  @Benchmark
  def foldLeftLargeChain: Int =
    largeChain.foldLeft(0)(_ + _)
  @Benchmark
  def foldLeftLargeChunk: Int =
    largeChunk.foldLeft(0)(_ + _)
  @Benchmark
  def foldLeftLargeVector: Int =
    largeVector.foldLeft(0)(_ + _)
  @Benchmark
  def foldLeftLargeList: Int =
    largeList.foldLeft(0)(_ + _)

  @Benchmark
  def mapLargeChain: Chain[Int] =
    largeChain.map(_ + 1)
  @Benchmark
  def mapLargeChunk: Chunk[Int] =
    largeChunk.map(_ + 1)
  @Benchmark
  def mapLargeVector: Vector[Int] =
    largeVector.map(_ + 1)
  @Benchmark
  def mapLargeList: List[Int] =
    largeList.map(_ + 1)
}
