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

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    chunk = Chunk.fromArray(array)
    vector = array.toVector
    list = array.toList
  }

  @Benchmark
  def mapChunk(): Chunk[Int] = chunk.map(_ * 2)

  @Benchmark
  def mapVector(): Vector[Int] = vector.map(_ * 2)

  @Benchmark
  def mapList(): List[Int] = list.map(_ * 2)

  @Benchmark
  def mapZIO(): Unit =
    Unsafe.unsafeCompat { implicit u =>
      BenchmarkUtil.unsafeRun(chunk.mapZIODiscard(_ => ZIO.unit))
    }

}
