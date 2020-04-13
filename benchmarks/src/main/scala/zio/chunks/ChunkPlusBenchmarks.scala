package zio.chunks

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{ Benchmark, BenchmarkMode, Mode, OutputTimeUnit, Param, Scope, Setup, State }

import zio.{ Chunk, NonEmptyChunk }
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class ChunkPlusBenchmarks {

  @Param(Array("1", "8", "64", "512", "4096", "32768"))
  var size: Int = _

  var chunk: NonEmptyChunk[Int]     = _
  var chunk_old: NonEmptyChunk[Int] = _
  var array: Array[Int]             = _
  var vector: Vector[Int]           = _

  @Setup
  def setup(): Unit = {
    chunk = chunkAdd
    chunk_old = chunkAdd_old
    array = arrayAdd
    vector = vectorAdd
  }

  @Benchmark
  def chunkAdd = (1 to size).foldLeft(Chunk(0))(_ + _)

  @Benchmark
  def chunkAdd_old = (1 to size).foldLeft(Chunk(0))(Chunk.addOld)

  @Benchmark
  def arrayAdd = (1 to size).foldLeft(Array(0))(_ :+ _)

  @Benchmark
  def vectorAdd = (1 to size).foldLeft(Vector(0))(_ :+ _)

  def transform(x: Int): Int = x + 1

  @Benchmark
  def chunkMap = chunk.map(transform)

  @Benchmark
  def chunkMap_old = chunk_old.map(transform)

  @Benchmark
  def arrayMap = array.map(transform)

  @Benchmark
  def vectorMap = vector.map(transform)
}
