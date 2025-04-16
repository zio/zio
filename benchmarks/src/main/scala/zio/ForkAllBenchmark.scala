package zio

import java.util.concurrent._
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@Threads(1)
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ForkAllBenchmark {

  @Param(Array("1", "128", "1024"))
  var count: Int = 0

  var z: ZIO[Any, Nothing, Chunk[Unit]] = _
  var zScoped: ZIO[Any, Nothing, Chunk[Unit]] = _
  var zScopedAlt: ZIO[Any, Nothing, Chunk[Unit]] = _

  @Setup
  def setup(): Unit = {
    val tasks =
      Chunk.fill(count) {
        ZIO.succeed(())
      }
    z = ZIO.forkAll(tasks).flatMap(_.join)
    zScoped = ZIO.scoped{
        ZIO.foreach(tasks)(_.forkScoped)
          .map(Fiber.collectAll(_))
          .flatMap(_.join)
      }
    zScopedAlt = ZIO.scopedWith { scope =>
        ZIO.foreach(tasks)(_.forkInAlt(scope))
          .map(Fiber.collectAll(_))
          .flatMap(_.join)
      }
  }

  @Benchmark
  def run(): Chunk[Unit] =
    unsafeRun(z)

  @Benchmark
  def scoped(): Chunk[Unit] =
    unsafeRun(zScoped)

  @Benchmark
  def scopedAlt(): Chunk[Unit] =
    unsafeRun(zScopedAlt)

}
