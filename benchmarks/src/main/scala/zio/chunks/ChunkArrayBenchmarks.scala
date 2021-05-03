package zio.chunks

import org.openjdk.jmh.annotations._
import zio.{State => _, _}

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
class ChunkArrayBenchmarks {
  @Param(Array("1000"))
  var size: Int = _

  var chunk: Chunk[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    val array = (1 to size).toArray
    chunk = Chunk.fromArray(array)
  }

  @Benchmark
  def fold(): Int = chunk.fold(0)(_ + _)

  @Benchmark
  def map(): Chunk[Int] = chunk.map(_ * 2)

  @Benchmark
  def flatMap(): Chunk[Int] = chunk.flatMap(n => Chunk(n + 2))

  @Benchmark
  def find(): Option[Int] = chunk.find(_ > 2)

  @Benchmark
  def mapM(): UIO[Unit] = chunk.mapM_(_ => ZIO.unit)

  @Benchmark
  def foldM(): UIO[Int] = chunk.foldM(0)((s, a) => ZIO.succeed(s + a))
}
