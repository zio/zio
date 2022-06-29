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

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    chunk = Chunk.fromArray(array)
    vector = array.toVector
    list = array.toList
  }

  @Benchmark
  def foldChunk(): Int = chunk.fold(0)(_ + _)

  @Benchmark
  def foldVector(): Int = vector.fold(0)(_ + _)

  @Benchmark
  def foldList(): Int = list.fold(0)(_ + _)

  @Benchmark
  def foldZIO(): Int =
    Unsafe.unsafely { implicit u =>
      BenchmarkUtil.unsafeRun(chunk.foldZIO[Any, Nothing, Int](0)((s, a) => ZIO.succeed(s + a)))
    }
}
