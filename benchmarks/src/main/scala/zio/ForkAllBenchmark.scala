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

  var z: ZIO[Any, Nothing, Chunk[Unit]]           = _
  var zScoped: ZIO[Any, Nothing, Chunk[Unit]]     = _
  var zScopedM: ZIO[Any, Nothing, Chunk[Unit]]    = _
  var zScopedAlt: ZIO[Any, Nothing, Chunk[Unit]]  = _
  var zScopedAltM: ZIO[Any, Nothing, Chunk[Unit]] = _

  @Setup
  def setup(): Unit = {
    val tasks =
      Chunk.fill(count) {
        ZIO.succeed(())
      }
    z = ZIO.forkAll(tasks).flatMap(_.join)
    zScoped = ZIO.scoped {
      ZIO
        .foreach(tasks)(_.forkScoped)
        .map(Fiber.collectAll(_))
        .flatMap(_.join)
    }
    zScopedM = ZIO.scopedWith { parentScope =>
      val nScopes = (count / 256) + 1
      val scopesZ =
        if (nScopes == 1)
          zio.Exit.succeed(zio.Chunk.single(parentScope))
        else
          ZIO
            .replicateZIO(nScopes)(parentScope.fork)
      scopesZ.flatMap { scopes_ =>
        val scopes = zio.Chunk.fromIterable(scopes_)
        var idx    = 0
        ZIO
          .foreach(tasks) { task =>
            val scope = scopes(idx % nScopes)
            idx += 1
            task.forkIn(scope)
          }
          .map(Fiber.collectAll(_))
          .flatMap(_.join)
      }
    }
    zScopedAlt = ZIO.scopedWith { scope =>
      ZIO
        .foreach(tasks)(_.forkInAlt(scope))
        .map(Fiber.collectAll(_))
        .flatMap(_.join)
    }
    zScopedAltM = ZIO.scopedWith { parentScope =>
      val nScopes = (count / 256) + 1
      val scopesZ =
        if (nScopes == 1)
          zio.Exit.succeed(zio.Chunk.single(parentScope))
        else
          ZIO
            .replicateZIO(nScopes)(parentScope.fork)
      scopesZ.flatMap { scopes_ =>
        val scopes = zio.Chunk.fromIterable(scopes_)
        var idx    = 0
        ZIO
          .foreach(tasks) { task =>
            val scope = scopes(idx % nScopes)
            idx += 1
            task.forkInAlt(scope)
          }
          .map(Fiber.collectAll(_))
          .flatMap(_.join)
      }
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

  @Benchmark
  def scopedAltM(): Chunk[Unit] =
    unsafeRun(zScopedAltM)

  @Benchmark
  def scopedM(): Chunk[Unit] =
    unsafeRun(zScopedM)

}
