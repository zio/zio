package zio

import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Deferred
import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class IOForkInterruptBenchmark {
  @Param(Array("100"))
  var size: Int = _

  @Benchmark
  def monixForkInterrupt(): Unit = {
    import monix.eval.Task

    def go(i: Int): monix.eval.Task[Unit] =
      if (i < size) Deferred[Task, Unit].flatMap { p1 =>
        Deferred[Task, Unit].flatMap { p2 =>
          p1.complete(())
            .flatMap(_ => Task.never)
            .guarantee(p2.complete(()))
            .start
            .flatMap(f => p1.get.flatMap(_ => f.cancel.flatMap(_ => p2.get.flatMap(_ => go(i + 1)))))
        }
      }
      else Task.unit

    go(0).runSyncUnsafe()
  }

  @Benchmark
  def catsForkInterrupt(): Unit = {
    import cats.effect.IO

    def go(i: Int): IO[Unit] =
      if (i < size)
        Deferred[IO, Unit].flatMap { p1 =>
          Deferred[IO, Unit].flatMap { p2 =>
            p1.complete(())
              .flatMap(_ => IO.never)
              .guarantee(p2.complete(()))
              .start
              .flatMap(f => p1.get.flatMap(_ => f.cancel.flatMap(_ => p2.get.flatMap(_ => go(i + 1)))))
          }
        }
      else IO.unit

    go(0).unsafeRunSync()
  }

  @Benchmark
  def zioForkInterrupt(): Unit = zioForkInterrupt(IOBenchmarks)

  @Benchmark
  def zioTracedForkInterrupt(): Unit = zioForkInterrupt(TracedRuntime)

  private[this] def zioForkInterrupt(runtime: Runtime[Any]): Unit = {
    def go(i: Int): UIO[Unit] =
      if (i < size) IO.never.fork.flatMap(_.interrupt *> go(i + 1))
      else IO.unit

    runtime.unsafeRun(go(0))
  }
}
