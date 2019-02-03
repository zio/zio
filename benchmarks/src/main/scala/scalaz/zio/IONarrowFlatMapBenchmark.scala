// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._

import scala.concurrent.Await

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IONarrowFlatMapBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def thunkNarrowFlatMap(): Int = {
    def loop(i: Int): Thunk[Int] =
      if (i < size) Thunk(i + 1).flatMap(loop)
      else Thunk(i)

    Thunk(0).unsafeRun()
  }

  @Benchmark
  def futureNarrowFlatMap(): Int = {
    import scala.concurrent.Future
    import scala.concurrent.duration.Duration.Inf

    def loop(i: Int): Future[Int] =
      if (i < size) Future(i + 1).flatMap(loop)
      else Future(i)

    Await.result(Future(0).flatMap(loop), Inf)
  }

  @Benchmark
  def completableFutureNarrowFlatMap(): Int = {
    import java.util.concurrent.CompletableFuture

    def loop(i: Int): CompletableFuture[Int] =
      if (i < size)
        CompletableFuture
          .completedFuture(i + 1)
          .thenCompose(loop)
      else CompletableFuture.completedFuture(i)

    CompletableFuture
      .completedFuture(0)
      .thenCompose(loop)
      .get()
  }

  @Benchmark
  def monoNarrowFlatMap(): Int = {
    import reactor.core.publisher.Mono
    def loop(i: Int): Mono[Int] =
      if (i < size) Mono.fromCallable(() => i + 1).flatMap(loop)
      else Mono.fromCallable(() => i)

    Mono
      .fromCallable(() => 0)
      .flatMap(loop)
      .block()
  }

  @Benchmark
  def rxSingleNarrowFlatMap(): Int = {
    import io.reactivex.Single

    def loop(i: Int): Single[Int] =
      if (i < size) Single.fromCallable(() => i + 1).flatMap(loop)
      else Single.fromCallable(() => i)

    Single
      .fromCallable(() => 0)
      .flatMap(loop)
      .blockingGet()
  }

  @Benchmark
  def twitterNarrowFlatMap(): Int = {
    import com.twitter.util.{ Await, Future }

    def loop(i: Int): Future[Int] =
      if (i < size) Future(i + 1).flatMap(loop)
      else Future(i)

    Await.result(
      Future(0)
        .flatMap(loop)
    )
  }

  @Benchmark
  def monixNarrowFlatMap(): Int = {
    import monix.eval.Task

    def loop(i: Int): Task[Int] =
      if (i < size) Task.eval(i + 1).flatMap(loop)
      else Task.eval(i)

    Task.eval(0).flatMap(loop).runSyncStep.right.get
  }

  @Benchmark
  def scalazNarrowFlatMap(): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) IO.succeedLazy[Int](i + 1).flatMap(loop)
      else IO.succeedLazy(i)

    unsafeRun(IO.succeedLazy(0).flatMap[Any, Nothing, Int](loop))
  }

  @Benchmark
  def catsNarrowFlatMap(): Int = {
    import cats.effect._

    def loop(i: Int): IO[Int] =
      if (i < size) IO(i + 1).flatMap(loop)
      else IO(i)

    IO(0).flatMap(loop).unsafeRunSync
  }
}
