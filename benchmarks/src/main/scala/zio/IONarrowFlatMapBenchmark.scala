package zio

import java.util.concurrent.TimeUnit

import scala.concurrent.Await

import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IONarrowFlatMapBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Benchmark
  def thunkNarrowFlatMap(): Int =
    Thunk(0).unsafeRun()

  @Benchmark
  def futureNarrowFlatMap(): Int = {
    import scala.concurrent.Future
    import scala.concurrent.duration.Duration.Inf

    def go(i: Int): Future[Int] =
      if (i < size) Future(i + 1).flatMap(go)
      else Future(i)

    Await.result(Future(0).flatMap(go), Inf)
  }

  @Benchmark
  def completableFutureNarrowFlatMap(): Int = {
    import java.util.concurrent.CompletableFuture

    def go(i: Int): CompletableFuture[Int] =
      if (i < size)
        CompletableFuture
          .completedFuture(i + 1)
          .thenCompose(go)
      else CompletableFuture.completedFuture(i)

    CompletableFuture
      .completedFuture(0)
      .thenCompose(go)
      .get()
  }

  @Benchmark
  def monoNarrowFlatMap(): Int = {
    import reactor.core.publisher.Mono
    def go(i: Int): Mono[Int] =
      if (i < size) Mono.fromCallable(() => i + 1).flatMap(go)
      else Mono.fromCallable(() => i)

    Mono
      .fromCallable(() => 0)
      .flatMap(go)
      .block()
  }

  @Benchmark
  def rxSingleNarrowFlatMap(): Int = {
    import io.reactivex.Single

    def go(i: Int): Single[Int] =
      if (i < size) Single.fromCallable(() => i + 1).flatMap(go(_))
      else Single.fromCallable(() => i)

    Single
      .fromCallable(() => 0)
      .flatMap(go(_))
      .blockingGet()
  }

  @Benchmark
  def twitterNarrowFlatMap(): Int = {
    import com.twitter.util.{ Await, Future }

    def go(i: Int): Future[Int] =
      if (i < size) Future(i + 1).flatMap(go)
      else Future(i)

    Await.result(
      Future(0)
        .flatMap(go)
    )
  }

  @Benchmark
  def monixNarrowFlatMap(): Int = {
    import monix.eval.Task

    def go(i: Int): Task[Int] =
      if (i < size) Task.eval(i + 1).flatMap(go)
      else Task.eval(i)

    Task.eval(0).flatMap(go).runSyncStep.fold(_ => sys.error("Either.right.get on Left"), identity)
  }

  @Benchmark
  def zioNarrowFlatMap(): Int = zioNarrowFlatMap(IOBenchmarks)

  @Benchmark
  def zioTracedNarrowFlatMap(): Int = zioNarrowFlatMap(TracedRuntime)

  private[this] def zioNarrowFlatMap(runtime: Runtime[Any]): Int = {
    def go(i: Int): UIO[Int] =
      if (i < size) IO.effectTotal[Int](i + 1).flatMap(go)
      else IO.effectTotal(i)

    runtime.unsafeRun(IO.effectTotal(0).flatMap[Any, Nothing, Int](go))
  }

  @Benchmark
  def catsNarrowFlatMap(): Int = {
    import cats.effect._

    def go(i: Int): IO[Int] =
      if (i < size) IO(i + 1).flatMap(go)
      else IO(i)

    IO(0).flatMap(go).unsafeRunSync()
  }
}
