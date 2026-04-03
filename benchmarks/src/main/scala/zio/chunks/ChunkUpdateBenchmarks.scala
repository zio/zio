package zio.chunks

import org.openjdk.jmh.annotations._
import zio.Chunk

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(3)
class ChunkUpdateBenchmarks {

  val chunk: Chunk[Int]   = Chunk.fill(10000)(0)
  val vector: Vector[Int] = Vector.fill(10000)(0)

  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def chunkUpdate(): Chunk[Int] = {
    var i       = 0
    var current = chunk

    while (i < size) {
      current = current.updated(i, i)
      i += 1
    }

    current
  }

  @Benchmark
  def vectorUpdate(): Vector[Int] = {
    var i       = 0
    var current = vector

    while (i < size) {
      current = current.updated(i, i)
      i += 1
    }

    current
  }
}
