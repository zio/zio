package zio.chunks
import zio._

class ChunkArrayBenchmarks {

  import java.util.concurrent.TimeUnit

  import org.openjdk.jmh.annotations._
  @State(Scope.Thread)
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  class ChunkArrayBenchmarks {

    @Param(Array("100", "1000", "10000"))
    var size: Int = _

    var chunk: Chunk[Int] = _

    var array: Array[Int] = _

    @Setup(Level.Trial)
    def setup() = {
      array = Array.fill(0)(size)
      chunk = Chunk.fromArray(array)
    }

    @Benchmark
    def chunkFold(): Int =
      chunk.fold(0)(_ + _)

    @Benchmark
    def chunkMap(): Chunk[Int] = chunk.map(_ * 2)

    @Benchmark
    def chunkFlatMap(): Chunk[Int] = chunk.flatMap(n => Chunk(n + 2))

    @Benchmark
    def chunkFind(): Option[Int] = chunk.find(_ > 2)

    @Benchmark
    def chunkMapM(): UIO[Unit] = chunk.mapM_(_ => ZIO.unit)

    @Benchmark
    def chunkFoldM(): UIO[Int] = chunk.foldM(0)((s, a) => ZIO.succeed(s + a))

  }

}
