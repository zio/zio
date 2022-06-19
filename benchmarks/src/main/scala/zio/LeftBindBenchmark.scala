package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import scala.concurrent.Await

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class LeftBindBenchmark {
  @Param(Array("10000"))
  var size: Int = _

  @Param(Array("100"))
  var depth: Int = _

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
      if (i % depth == 0) Mono.fromSupplier(() => i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => Mono.fromSupplier(() => i))
      else Mono.fromSupplier(() => i)

    Mono
      .fromSupplier(() => 0)
      .flatMap(loop)
      .block()
  }

  @Benchmark
  def rxSingleLeftBindBenchmark(): Int = {
    import io.reactivex.Single

    def loop(i: Int): Single[Int] =
      if (i % depth == 0) Single.fromCallable(() => i + 1).flatMap(loop(_))
      else if (i < size) loop(i + 1).flatMap(i => Single.fromCallable(() => i))
      else Single.fromCallable(() => i)

    Single
      .fromCallable(() => 0)
      .flatMap(loop(_))
      .blockingGet()
  }

  @Benchmark
  def twitterLeftBindBenchmark(): Int = {
    import com.twitter.util.{Await, Future}

    def loop(i: Int): Future[Int] =
      if (i % depth == 0) Future(i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => Future(i))
      else Future(i)

    Await.result(
      Future(0)
        .flatMap(loop)
    )
  }

  @Benchmark
  def zioLeftBindBenchmark: Int = zioLeftBindBenchmark(BenchmarkUtil)

  private[this] def zioLeftBindBenchmark(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i % depth == 0) ZIO.succeed[Int](i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => ZIO.succeed(i))
      else ZIO.succeed(i)

    Unsafe.unsafeCompat { implicit u =>
      runtime.unsafeRun(ZIO.succeed(0).flatMap[Any, Nothing, Int](loop))
    }
  }

  @Benchmark
  def catsLeftBindBenchmark(): Int = {
    import cats.effect._

    def loop(i: Int): IO[Int] =
      if (i % depth == 0) IO(i + 1).flatMap(loop)
      else if (i < size) loop(i + 1).flatMap(i => IO(i))
      else IO(i)

    IO(0).flatMap(loop).unsafeRunSync()
  }
}
