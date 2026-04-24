package zio.chunks

import org.openjdk.jmh.annotations._
import zio.Chunk

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 10)
@Measurement(iterations = 10, time = 10)
class ChunkPrependBenchmarks {

  val chunk: Chunk[Int]   = Chunk(1)
  val vector: Vector[Int] = Vector(1)
  val array: Array[Int]   = Array(1)
  val list: List[Int]     = List(1)

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

  @Benchmark
  def arrayPrepend(): Array[Int] = {
    var i       = 0
    var current = array

    while (i < size) {
      current = i +: current
      i += 1
    }

    current
  }

  @Benchmark
  def listPrepend(): List[Int] = {
    var i       = 0
    var current = list

    while (i < size) {
      current = i +: current
      i += 1
    }

    current
  }
}
