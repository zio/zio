package zio.chunks

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 10)
class ChunkFoldBenchmarks {
  @Param(Array("1000"))
  var size: Int = _

  var chunk: Chunk[Int]   = _
  var vector: Vector[Int] = _
  var list: List[Int]     = _
  var array: Array[Int]   = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val arr = (1 to size).toArray
    chunk = Chunk.fromArray(arr)
    vector = arr.toVector
    list = arr.toList
    array = arr
  }

  @Benchmark
  def foldChunk(): Int = chunk.fold(0)(_ + _)

  @Benchmark
  def foldArray(): Int = array.fold(0)(_ + _)

  @Benchmark
  def foldRightChunk(): Int = chunk.foldRight(0)(_ + _)

  @Benchmark
  def foldRightArray(): Int = array.foldRight(0)(_ + _)

  @Benchmark
  def foldVector(): Int = vector.fold(0)(_ + _)

  @Benchmark
  def foldList(): Int = list.fold(0)(_ + _)

  @Benchmark
  def foldZIO(): Int =
    BenchmarkUtil.unsafeRun(chunk.foldZIO[Any, Nothing, Int](0)((s, a) => ZIO.succeed(s + a)))
}
