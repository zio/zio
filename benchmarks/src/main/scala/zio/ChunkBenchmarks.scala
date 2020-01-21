package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class ChunkBenchmarks {

  @Param(Array("1000"))
  var chunkSize: Int = _

  @Param(Array("1000"))
  var count: Int = _

  var chunk: Chunk[Int] = _

  var array: Array[Int] = _

  @Setup
  def createChunk() = {
    chunk = Chunk.fromArray(Array.fill(0)(chunkSize))
  }

  @Setup
  def createArray() = {
    array = Array.fill(0)(chunkSize)
  }

  @Benchmark
  def zioRandomAccess(): Int = {
    var i = 0
    var sum = 0
    while (i < chunkSize) {
      sum += chunk(i)
      i += 1
    }
    sum
  }

  @Benchmark
  def arrayRandomAccess(): Int = {
    var i = 0
    var sum = 0
    while (i < chunkSize) {
      sum += array(i)
      i += 1
    }
    sum
  }


}





