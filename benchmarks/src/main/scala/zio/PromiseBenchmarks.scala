package zio

import cats.effect.kernel.Deferred
import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(value = 3)
class PromiseBenchmarks {

  val size = 100000

  val ints: List[Int] = List.range(0, size)

  @Benchmark
  def zioPromiseAwaitDone(): Unit = {

    val io = ZIO.foreachDiscard(ints) { _ =>
      Promise.make[Nothing, Unit].flatMap { promise =>
        promise.succeed(()) *> promise.await
      }
    }

    unsafeRun(io)
  }

  @Benchmark
  def catsPromiseAwaitDone(): Unit = {

    val io = catsForeachDiscard(List.range(1, size)) { _ =>
      Deferred[CIO, Unit].flatMap { promise =>
        promise.complete(()).flatMap(_ => promise.get)
      }
    }

    io.unsafeRunSync()
  }
}
