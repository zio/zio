package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import scala.concurrent.Await

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class NarrowFlatMapBenchmark {
  @Param(Array("1000"))
  var size: Int = _

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
      if (i < size) Single.fromCallable(() => i + 1).flatMap(loop(_))
      else Single.fromCallable(() => i)

    Single
      .fromCallable(() => 0)
      .flatMap(loop(_))
      .blockingGet()
  }

  @Benchmark
  def twitterNarrowFlatMap(): Int = {
    import com.twitter.util.{Await, Future}

    def loop(i: Int): Future[Int] =
      if (i < size) Future(i + 1).flatMap(loop)
      else Future(i)

    Await.result(
      Future(0)
        .flatMap(loop)
    )
  }

  @Benchmark
  def zioNarrowFlatMap(): Int = zioNarrowFlatMap(BenchmarkUtil)

  private[this] def zioNarrowFlatMap(runtime: Runtime[Any]): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < size) ZIO.succeed[Int](i + 1).flatMap(loop)
      else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(ZIO.succeed(0).flatMap[Any, Nothing, Int](loop)).getOrThrowFiberFailure()
    }
  }

  @Benchmark
  def catsNarrowFlatMap(): Int = {
    import cats.effect._

    def loop(i: Int): IO[Int] =
      if (i < size) IO(i + 1).flatMap(loop)
      else IO(i)

    IO(0).flatMap(loop).unsafeRunSync()
  }
}
