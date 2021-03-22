package zio.chunks

import org.openjdk.jmh.annotations._
import zio.Chunk

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ChunkConcatBenchmarks {

  val chunk: Chunk[Int] = Chunk.empty

  @Param(Array("10000"))
  var size: Int = _

  def concatBalanced(n: Int): Chunk[Int] =
    if (n == 0) Chunk.empty
    else if (n == 1) Chunk(1)
    else concatBalanced(n / 2) ++ concatBalanced(n / 2)

  @Benchmark
  def leftConcat(): Chunk[Int] = {
    var i       = 1
    var current = chunk

    while (i < size) {
      current = Chunk(i) ++ current
      i += 1
    }

    current
  }

  @Benchmark
  def rightConcat(): Chunk[Int] = {
    var i       = 1
    var current = chunk

    while (i < size) {
      current = current ++ Chunk(i)
      i += 1
    }

    current
  }

  @Benchmark
  def balancedConcat(): Chunk[Int] =
    concatBalanced(size)
}
