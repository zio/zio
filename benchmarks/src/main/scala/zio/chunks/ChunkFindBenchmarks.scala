package zio.chunks

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 10)
class ChunkFindBenchmarks {
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
  def find(): Option[Int] = chunk.find(_ > 2)
}
