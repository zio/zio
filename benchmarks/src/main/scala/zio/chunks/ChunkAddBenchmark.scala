package zio.chunks

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio.Chunk

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkAddBenchmarks {

  val chunk  = Chunk(1)
  val vector = Vector(1)

  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def chunkAdd(): Chunk[Int] = {
    var i       = 0
    var current = chunk

    while (i < size) {
      current = current :+ 2
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
