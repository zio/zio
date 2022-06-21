package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration.Inf

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
class DeepAsyncBenchmark {
  @Param(Array("50"))
  var depth: Int = _

  @Benchmark
  def futureDeepAsyncOne(): Int = {
    def recurse(n: Int): Future[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => Future.successful(i + 1))
      else {
        val p = scala.concurrent.Promise[Int]()
        Future(p.success(0))
        p.future
      }

    Await.result(recurse(depth), Inf)
  }

  @Benchmark
  def futureDeepAsyncMany(): Int = {
    def recurse(n: Int): Future[Int] =
      if (n > 0) recurse(n - 1).flatMap { i =>
        val p = scala.concurrent.Promise[Int]()
        Future(p.success(i + 1))
        p.future
      }
      else {
        val p = scala.concurrent.Promise[Int]()
        Future(p.success(0))
        p.future
      }

    Await.result(recurse(depth), Inf)
  }

  @Benchmark
  def catsDeepAsyncOne(): Int = {
    import cats.effect._

    def recurse(n: Int): IO[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => IO(i + 1))
      else IO.async_[Int](k => k(Right(0)))

    recurse(depth).unsafeRunSync()
  }

  @Benchmark
  def catsDeepAsyncMany(): Int = {
    import cats.effect._

    def recurse(n: Int): IO[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => IO.async_(k => k(Right(i + 1))))
      else IO.async_[Int](k => k(Right(0)))

    recurse(depth).unsafeRunSync()
  }

  @Benchmark
  def zioDeepAsyncOne(): Int = {
    import zio.BenchmarkUtil._

    def recurse(n: Int): UIO[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => ZIO.succeed(i + 1))
      else ZIO.async[Any, Nothing, Int](k => k(ZIO.succeedNow(0)))

    unsafeRun(recurse(depth))
  }

  @Benchmark
  def zioDeepAsyncMany(): Int = {
    import zio.BenchmarkUtil._

    def recurse(n: Int): UIO[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => ZIO.async[Any, Nothing, Int](k => k(ZIO.succeedNow(i + 1))))
      else ZIO.async[Any, Nothing, Int](k => k(ZIO.succeedNow(0)))

    unsafeRun(recurse(depth))
  }
}
