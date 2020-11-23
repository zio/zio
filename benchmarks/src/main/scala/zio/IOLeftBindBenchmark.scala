package zio

import java.util.concurrent.TimeUnit

import scala.concurrent.Await

import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._

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
    def go(i: Int): Thunk[Int] =
      if (i % depth == 0) Thunk(i + 1).flatMap(go)
      else if (i < size) go(i + 1).flatMap(i => Thunk(i))
      else Thunk(i)

    Thunk(0).unsafeRun()
  }

  @Benchmark
  def futureLeftBindBenchmark(): Int = {
    import scala.concurrent.Future
    import scala.concurrent.duration.Duration.Inf

    def go(i: Int): Future[Int] =
      if (i % depth == 0) Future(i + 1).flatMap(go)
      else if (i < size) go(i + 1).flatMap(i => Future(i))
      else Future(i)

    Await.result(Future(0).flatMap(go), Inf)
  }

  @Benchmark
  def completableFutureLeftBindBenchmark(): Int = {
    import java.util.concurrent.CompletableFuture

    def go(i: Int): CompletableFuture[Int] =
      if (i % depth == 0) CompletableFuture.completedFuture(i + 1).thenCompose(go)
      else if (i < size) go(i + 1).thenCompose(i => CompletableFuture.completedFuture(i))
      else CompletableFuture.completedFuture(i)

    CompletableFuture
      .completedFuture(0)
      .thenCompose(go)
      .get()
  }

  @Benchmark
  def monoLeftBindBenchmark(): Int = {
    import reactor.core.publisher.Mono

    def go(i: Int): Mono[Int] =
      if (i % depth == 0) Mono.fromSupplier(() => i + 1).flatMap(go)
      else if (i < size) go(i + 1).flatMap(i => Mono.fromSupplier(() => i))
      else Mono.fromSupplier(() => i)

    Mono
      .fromSupplier(() => 0)
      .flatMap(go)
      .block()
  }

  @Benchmark
  def rxSingleLeftBindBenchmark(): Int = {
    import io.reactivex.Single

    def go(i: Int): Single[Int] =
      if (i % depth == 0) Single.fromCallable(() => i + 1).flatMap(go(_))
      else if (i < size) go(i + 1).flatMap(i => Single.fromCallable(() => i))
      else Single.fromCallable(() => i)

    Single
      .fromCallable(() => 0)
      .flatMap(go(_))
      .blockingGet()
  }

  @Benchmark
  def twitterLeftBindBenchmark(): Int = {
    import com.twitter.util.{ Await, Future }

    def go(i: Int): Future[Int] =
      if (i % depth == 0) Future(i + 1).flatMap(go)
      else if (i < size) go(i + 1).flatMap(i => Future(i))
      else Future(i)

    Await.result(
      Future(0)
        .flatMap(go)
    )
  }

  @Benchmark
  def monixLeftBindBenchmark(): Int = {
    import monix.eval.Task

    def go(i: Int): Task[Int] =
      if (i % depth == 0) Task.eval(i + 1).flatMap(go)
      else if (i < size) go(i + 1).flatMap(i => Task.eval(i))
      else Task.eval(i)

    Task.eval(0).flatMap(go).runSyncStep.fold(_ => sys.error("Either.right.get on Left"), identity)
  }

  @Benchmark
  def zioLeftBindBenchmark: Int = zioLeftBindBenchmark(IOBenchmarks)

  @Benchmark
  def zioTracedLeftBindBenchmark(): Int = zioLeftBindBenchmark(TracedRuntime)

  private[this] def zioLeftBindBenchmark(runtime: Runtime[Any]): Int = {
    def go(i: Int): UIO[Int] =
      if (i % depth == 0) IO.effectTotal[Int](i + 1).flatMap(go)
      else if (i < size) go(i + 1).flatMap(i => IO.effectTotal(i))
      else IO.effectTotal(i)

    runtime.unsafeRun(IO.effectTotal(0).flatMap[Any, Nothing, Int](go))
  }

  @Benchmark
  def catsLeftBindBenchmark(): Int = {
    import cats.effect._

    def go(i: Int): IO[Int] =
      if (i % depth == 0) IO(i + 1).flatMap(go)
      else if (i < size) go(i + 1).flatMap(i => IO(i))
      else IO(i)

    IO(0).flatMap(go).unsafeRunSync()
  }
}
