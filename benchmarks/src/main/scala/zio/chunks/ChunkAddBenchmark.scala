package zio.chunks

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio.Chunk

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkAddBenchmarks {

  var chunk: Chunk[Int]   = _
  var vector: Vector[Int] = _

  @Param(Array("10000"))
  var size: Int = _

  @Setup(Level.Trial)
  def setup() =
    chunk = Chunk(1)
  vector = Vector(1)

  @Benchmark
  def chunkAdd(): Chunk[Int] = {
    var i       = 0
    var current = chunk

    while (i < size) {
      current = current + 2
      i += 1
    }

    current
  }

  @Benchmark
  def vectorAdd(): Vector[Int] = {
    var i       = 0
    var current = vector

    while (i < size) {
      current = current :+ 2
      i += 1
    }

    current
  }
}
