// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._

import scala.concurrent.Await

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IODeepFlatMapBenchmark {
  @Param(Array("20"))
  var depth: Int = _

  @Benchmark
  def thunkDeepFlatMap(): BigInt = {
    def fib(n: Int): Thunk[BigInt] =
      if (n <= 1) Thunk(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => Thunk(a + b))
        }

    fib(depth).unsafeRun()
  }

  @Benchmark
  def futureDeepFlatMap(): BigInt = {
    import scala.concurrent.Future
    import scala.concurrent.duration.Duration.Inf

    def fib(n: Int): Future[BigInt] =
      if (n <= 1) Future(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => Future(a + b))
        }

    Await.result(fib(depth), Inf)
  }

  @Benchmark
  def completableFutureDeepFlatMap(): BigInt = {
    import java.util.concurrent.CompletableFuture

    def fib(n: Int): CompletableFuture[BigInt] =
      if (n <= 1) CompletableFuture.completedFuture(n)
      else
        fib(n - 1).thenCompose { a =>
          fib(n - 2).thenCompose(b => CompletableFuture.completedFuture(a + b))
        }

    fib(depth)
      .get()
  }

  @Benchmark
  def monoDeepFlatMap(): BigInt = {
    import reactor.core.publisher.Mono

    def fib(n: Int): Mono[BigInt] =
      if (n <= 1) Mono.fromSupplier(() => n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => Mono.fromSupplier(() => a + b))
        }

    fib(depth)
      .block()
  }

  @Benchmark
  def rxSingleDeepFlatMap(): BigInt = {
    import io.reactivex.Single

    def fib(n: Int): Single[BigInt] =
      if (n <= 1) Single.fromCallable(() => n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => Single.fromCallable(() => a + b))
        }

    fib(depth)
      .blockingGet()
  }

  @Benchmark
  def twitterDeepFlatMap(): BigInt = {
    import com.twitter.util.{ Await, Future }

    def fib(n: Int): Future[BigInt] =
      if (n <= 1) Future(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => Future(a + b))
        }

    Await.result(fib(depth))
  }

  @Benchmark
  def monixDeepFlatMap(): BigInt = {
    import monix.eval.Task

    def fib(n: Int): Task[BigInt] =
      if (n <= 1) Task.eval(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => Task.eval(a + b))
        }

    fib(depth).runSyncStep.right.get
  }

  @Benchmark
  def scalazDeepFlatMap(): BigInt = {
    def fib(n: Int): UIO[BigInt] =
      if (n <= 1) IO.succeedLazy[BigInt](n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => IO.succeedLazy(a + b))
        }

    unsafeRun(fib(depth))
  }

  @Benchmark
  def catsDeepFlatMap(): BigInt = {
    import cats.effect._

    def fib(n: Int): IO[BigInt] =
      if (n <= 1) IO(n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => IO(a + b))
        }

    fib(depth).unsafeRunSync
  }
}
