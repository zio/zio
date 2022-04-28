package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import scala.concurrent.Await

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class ShallowAttemptBenchmark {
  case class ZIOError(message: String)

  @Param(Array("1000"))
  var depth: Int = _

  @Benchmark
  def futureShallowAttempt(): BigInt = {
    import scala.concurrent.Future
    import scala.concurrent.duration.Duration.Inf

    def throwup(n: Int): Future[BigInt] =
      if (n == 0) throwup(n + 1) recover { case _ => 0 }
      else if (n == depth) Future(1)
      else
        throwup(n + 1).recover { case _ => 0 }
          .flatMap(_ => Future.failed(new Exception("Oh noes!")))

    Await.result(throwup(0), Inf)
  }

  @Benchmark
  def completableFutureShallowAttempt(): BigInt = {
    import java.util.concurrent.CompletableFuture

    def throwup(n: Int): CompletableFuture[BigInt] =
      if (n == 0) throwup(n + 1).exceptionally(_ => 0)
      else if (n == depth) CompletableFuture.completedFuture(1)
      else
        throwup(n + 1)
          .exceptionally(_ => 0)
          .thenCompose { _ =>
            val f = new CompletableFuture[BigInt]()
            f.completeExceptionally(new Exception("Oh noes!"))
            f
          }

    throwup(0)
      .get()
  }

  @Benchmark
  def monoShallowAttempt(): BigInt = {
    import reactor.core.publisher.Mono

    def throwup(n: Int): Mono[BigInt] =
      if (n == 0) throwup(n + 1).onErrorReturn(0)
      else if (n == depth) Mono.fromCallable(() => 1)
      else
        throwup(n + 1)
          .onErrorReturn(0)
          .flatMap(_ => Mono.error(new Exception("Oh noes!")))

    throwup(0)
      .block()
  }

  @Benchmark
  def rxSingleShallowAttempt(): BigInt = {
    import io.reactivex.Single

    def throwup(n: Int): Single[BigInt] =
      if (n == 0) throwup(n + 1).onErrorReturn(_ => 0)
      else if (n == depth) Single.fromCallable(() => 1)
      else
        throwup(n + 1)
          .onErrorReturn(_ => 0)
          .flatMap(_ => Single.error(new Exception("Oh noes!")))

    throwup(0)
      .blockingGet()
  }

  @Benchmark
  def twitterShallowAttempt(): BigInt = {
    import com.twitter.util.{Await, Future}
    import com.twitter.util.{Return, Throw}

    def throwup(n: Int): Future[BigInt] =
      if (n == 0) throwup(n + 1).rescue { case _ =>
        Future.value(0)
      }
      else if (n == depth) Future(1)
      else
        throwup(n + 1).transform {
          case Throw(_)  => Future.value[BigInt](0)
          case Return(_) => Future.exception[BigInt](new Error("Oh noes!"))
        }

    Await.result(throwup(0))
  }

  @Benchmark
  def zioShallowAttempt(): BigInt = {
    def throwup(n: Int): IO[ZIOError, BigInt] =
      if (n == 0) throwup(n + 1).fold[BigInt](_ => 50, identity)
      else if (n == depth) ZIO.succeed(1)
      else throwup(n + 1).foldZIO[Any, ZIOError, BigInt](_ => ZIO.succeedNow(0), _ => ZIO.fail(ZIOError("Oh noes!")))

    unsafeRun(throwup(0))
  }

  @Benchmark
  def zioShallowAttemptBaseline(): BigInt = {
    def throwup(n: Int): IO[Error, BigInt] =
      if (n == 0) throwup(n + 1).fold[BigInt](_ => 50, identity)
      else if (n == depth) ZIO.succeed(1)
      else throwup(n + 1).foldZIO[Any, Error, BigInt](_ => ZIO.succeedNow(0), _ => ZIO.fail(new Error("Oh noes!")))

    unsafeRun(throwup(0))
  }

  @Benchmark
  def catsShallowAttempt(): BigInt = {
    import cats.effect._

    def throwup(n: Int): IO[BigInt] =
      if (n == 0) throwup(n + 1).attempt.map(_.fold(_ => 0, a => a))
      else if (n == depth) IO(1)
      else
        throwup(n + 1).attempt.flatMap {
          case Left(_)  => IO(0)
          case Right(_) => IO.raiseError(new Error("Oh noes!"))
        }

    throwup(0).unsafeRunSync()
  }
}
