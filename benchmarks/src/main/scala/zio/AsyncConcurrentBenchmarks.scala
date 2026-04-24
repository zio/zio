package zio
import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import cats.syntax.all._
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import java.util.concurrent.LinkedBlockingQueue
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(1)
class AsyncConcurrentBenchmarks {

  val concurrency = 1000

  @Benchmark
  def catsAsyncConcurrent(): Any = {
    def effect(depth: Int, ks: LinkedBlockingQueue[Either[Throwable, Int] => Unit]): CIO[Int] =
      if (depth >= 1) CIO.pure(depth)
      else {
        CIO
          .async_[Int] { k =>
            ks.offer(k)
            ()
          }
          .flatMap(result1 => effect(depth + 1, ks).map(result2 => result1 + result2))
      }

    val ks = new LinkedBlockingQueue[Either[Throwable, Int] => Unit]()

    (0 to concurrency).toList.traverse { _ =>
      effect(0, ks).start
    }.flatMap { fibers =>
      for (_ <- 0 to concurrency) ks.take()(Right(42))

      fibers.traverse { fiber =>
        fiber.join
      }
    }.unsafeRunSync()
  }

  @Benchmark
  def zioAsyncConcurent(): Any = {
    def effect(depth: Int, ks: LinkedBlockingQueue[UIO[Int] => Unit]): UIO[Int] =
      if (depth >= 1) ZIO.succeed(depth)
      else {
        ZIO
          .async[Any, Nothing, Int] { k =>
            ks.offer(k)
            ()
          }
          .flatMap(result1 => effect(depth + 1, ks).map(result2 => result1 + result2))
      }

    val ks = new LinkedBlockingQueue[UIO[Int] => Unit]()

    unsafeRun {
      ZIO
        .foreach(0 to concurrency) { _ =>
          effect(0, ks).forkDaemon
        }
        .flatMap { fibers =>
          for (_ <- 0 to concurrency) ks.take()(Exit.succeed(42))

          ZIO.foreach(fibers) { fiber =>
            fiber.await
          }
        }
    }
  }
}
