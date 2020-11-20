package zio.chunks

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio.Chunk

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkPrependBenchmarks {

  val chunk: Chunk[Int]   = Chunk(1)
  val vector: Vector[Int] = Vector(1)

  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def chunkPrepend(): Chunk[Int] = {
    var i       = 0
    var current = chunk

    while (i < size) {
      current = i +: current
      i += 1
    }

    current
  }

  @Benchmark
  def vectorPrepend(): Vector[Int] = {
    var i       = 0
    var current = vector

    while (i < size) {
      current = i +: current
      i += 1
    }

    current
  }
}
