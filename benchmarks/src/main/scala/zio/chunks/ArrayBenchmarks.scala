package zio.chunks

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio.Chunk
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class ArrayBenchmarks {

  @Param(Array("100", "1000", "10000"))
  var array: Array[Int] = _

  var chunk: Chunk[Int] = _

  var chunkSize: Int = _

  @Setup(Level.Trial)
  def setup() = {
    array = Array.fill(0)(chunkSize)
    chunk = Chunk.fromArray(array)
  }

  @Benchmark
  def arrayFold(): Int =
    array.sum

  @Benchmark
  def arrayMap(): Array[Int] = array.map(_ * 2)

  @Benchmark
  def arrayMapOptimized(): Array[Int] = {
    var i   = 0
    val len = array.length

    while (i < len) {
      array(i) = array(i) * 2
      i = i + 1
    }

    array
  }

  @Benchmark
  def arrayFind(): Option[Int] = array.find(_ > 2)

  def arrayFindOptimized(): Option[Int] = {
    var i   = 0
    val len = array.length

    while (i < len) {
      if (array(i) > 2) return Some(array(i))
      i = i + 1
    }

    None
  }

  @Benchmark
  def arrayFlatMap(): Array[Int] = array.flatMap(n => Array(n + 2))

}
