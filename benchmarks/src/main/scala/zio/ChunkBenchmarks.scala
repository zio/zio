package zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class ChunkBenchmarks {

  @Param(Array("100", "1000", "10000"))
  var chunkSize: Int = _

  var chunk: Chunk[Int] = _

  var array: Array[Int] = _

  @Setup(Level.Trial)
  def setup() = {
    array = Array.fill(0)(chunkSize)
    chunk = Chunk.fromArray(array)
  }

  @Benchmark
  def chunkFold(): Int =
    chunk.fold(0)(_ + _)

  @Benchmark
  def arrayFold(): Int =
    array.sum
  @Benchmark
  def chunkMap(): Chunk[Int] = chunk.map(_ * 2)

  @Benchmark
  def arrayMap(): Array[Int] = array.map(_ * 2)

  @Benchmark
  def chunkFlatMap(): Chunk[Int] = chunk.flatMap(n => Chunk(n + 2))

  @Benchmark
  def arrayFlatMap(): Array[Int] = array.flatMap(n => Array(n + 2))

  @Benchmark
  def chunkFind(): Option[Int] = chunk.find(_ > 2)

  @Benchmark
  def arrayFind(): Option[Int] = array.find(_ > 2)

  @Benchmark
  def chunkMapM(): UIO[Unit] = chunk.mapM_(_ => ZIO.unit)

  @Benchmark
  def chunkFoldM(): UIO[Int] = chunk.foldM(0)((s, a) => ZIO.succeed(s + a))

}
