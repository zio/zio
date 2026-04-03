package zio.chunks

import org.openjdk.jmh.annotations._
import zio.Chunk

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(value = 3)
class ChunkConcatBenchmarks {

  @Param(Array("10000"))
  var size: Int = _

  var chunk0: Chunk[Int]        = _
  var chunk1: Chunk[Int]        = _
  var chunk10: Chunk[Int]       = _
  var chunkHalfSize: Chunk[Int] = _

  @Setup
  def setUp(): Unit = {
    chunk0 = Chunk.empty
    chunk1 = Chunk.single(1)
    chunk10 = Chunk.fill(10)(1)
    chunkHalfSize = Chunk.fill(size / 2)(1)
  }

  @Benchmark
  def leftConcat0(): Chunk[Int] = {
    var i       = 1
    var current = chunk0

    while (i < size) {
      current = chunk0 ++ current
      i += 1
    }

    current
  }

  @Benchmark
  def leftConcat1(): Chunk[Int] = {
    var i       = 1
    var current = chunk1

    while (i < size) {
      current = chunk1 ++ current
      i += 1
    }

    current
  }

  @Benchmark
  def leftConcat10(): Chunk[Int] = {
    var i       = 1
    var current = chunk10

    while (i < size) {
      current = chunk10 ++ current
      i += 10
    }

    current
  }

  @Benchmark
  def rightConcat0(): Chunk[Int] = {
    var i       = 1
    var current = chunk0

    while (i < size) {
      current = current ++ chunk0
      i += 1
    }

    current
  }

  @Benchmark
  def rightConcat1(): Chunk[Int] = {
    var i       = 1
    var current = chunk1

    while (i < size) {
      current = current ++ chunk1
      i += 1
    }

    current
  }

  @Benchmark
  def rightConcat10(): Chunk[Int] = {
    var i       = 1
    var current = chunk10

    while (i < size) {
      current = current ++ chunk10
      i += 10
    }

    current
  }
  @Benchmark
  def balancedConcatOnce(): Chunk[Int] =
    chunkHalfSize ++ chunkHalfSize

  @Benchmark
  def balancedConcatRecursive(): Chunk[Int] =
    concatBalancedRec(size)

  def concatBalancedRec(n: Int): Chunk[Int] =
    if (n == 0) Chunk.empty
    else if (n == 1) chunk1
    else concatBalancedRec(n / 2) ++ concatBalancedRec(n / 2)
}
