// Copyright (C) 2017 John A. De Goes. All rights reserved.
package scalaz.zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._

import scala.concurrent.Await

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IOLeftBindBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Param(Array("100"))
  var depth: Int = _

  @Benchmark
  def thunkLeftBindBenchmark(): Int = {
    def loop(i: Int): Thunk[Int] =
      if (i % depth == 0) Thunk(i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => Thunk(i))
      else Thunk(i)

    Thunk(0).unsafeRun()
  }

  @Benchmark
  def futureLeftBindBenchmark(): Int = {
    import scala.concurrent.Future
    import scala.concurrent.duration.Duration.Inf

    def loop(i: Int): Future[Int] =
      if (i % depth == 0) Future(i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => Future(i))
      else Future(i)

    Await.result(Future(0).flatMap(loop), Inf)
  }

  @Benchmark
  def completableFutureLeftBindBenchmark(): Int = {
    import java.util.concurrent.CompletableFuture

    def loop(i: Int): CompletableFuture[Int] =
      if (i % depth == 0) CompletableFuture.completedFuture(i + 1).thenCompose(loop)
      else if (i < size) loop(i + 1).thenCompose(i => CompletableFuture.completedFuture(i))
      else CompletableFuture.completedFuture(i)

    CompletableFuture
      .completedFuture(0)
      .thenCompose(loop)
      .get()
  }

  @Benchmark
  def monoLeftBindBenchmark(): Int = {
    import reactor.core.publisher.Mono

    def loop(i: Int): Mono[Int] =
      if (i % depth == 0) Mono.just(i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => Mono.just(i))
      else Mono.just(i)

    Mono
      .just(0)
      .flatMap(loop)
      .block()
  }

  @Benchmark
  def rxSingleLeftBindBenchmark(): Int = {
    import io.reactivex.Single

    def loop(i: Int): Single[Int] =
      if (i % depth == 0) Single.just(i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => Single.just(i))
      else Single.just(i)

    Single
      .just(0)
      .flatMap(loop)
      .blockingGet()
  }

  @Benchmark
  def monixLeftBindBenchmark(): Int = {
    import monix.eval.Task

    def loop(i: Int): Task[Int] =
      if (i % depth == 0) Task.eval(i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => Task.eval(i))
      else Task.eval(i)

    Task.eval(0).flatMap(loop).runSyncStep.right.get
  }

  @Benchmark
  def scalazLeftBindBenchmark(): Int = {
    def loop(i: Int): IO[Nothing, Int] =
      if (i % depth == 0) IO.succeedLazy[Int](i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => IO.succeedLazy(i))
      else IO.succeedLazy(i)

    unsafeRun(IO.succeedLazy[Int](0).flatMap(loop))
  }

  @Benchmark
  def catsLeftBindBenchmark(): Int = {
    import cats.effect._

    def loop(i: Int): IO[Int] =
      if (i % depth == 0) IO(i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => IO(i))
      else IO(i)

    IO(0).flatMap(loop).unsafeRunSync
  }
}
