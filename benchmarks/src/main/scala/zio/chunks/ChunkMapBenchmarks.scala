package zio.chunks

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 10)
class ChunkMapBenchmarks {
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
  def mapChunk(): Chunk[Int] = chunk.map(_ * 2)

  @Benchmark
  def mapArray(): Array[Int] = array.map(_ * 2)

  @Benchmark
  def mapVector(): Vector[Int] = vector.map(_ * 2)

  @Benchmark
  def mapList(): List[Int] = list.map(_ * 2)

  @Benchmark
  def mapZIO(): Unit =
    BenchmarkUtil.unsafeRun(chunk.mapZIODiscard(_ => ZIO.unit))

}
