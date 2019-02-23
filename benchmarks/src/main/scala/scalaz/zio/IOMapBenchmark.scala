package scalaz.zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._

import scala.annotation.tailrec

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IOMapBenchmark {
  @Param(Array("500"))
  var depth: Int = _

  @Benchmark
  def thunkMap(): BigInt = {
    @tailrec
    def sumTo(t: Thunk[BigInt], n: Int): Thunk[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    sumTo(Thunk(0), depth).unsafeRun()
  }

  @Benchmark
  def futureMap(): BigInt = {
    import scala.concurrent.{ Await, Future }
    import scala.concurrent.duration.Duration.Inf

    @tailrec
    def sumTo(t: Future[BigInt], n: Int): Future[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    Await.result(sumTo(Future(0), depth), Inf)
  }

  @Benchmark
  def completableFutureMap(): BigInt = {
    import java.util.concurrent.CompletableFuture

    @tailrec
    def sumTo(t: CompletableFuture[BigInt], n: Int): CompletableFuture[BigInt] =
      if (n <= 1) t
      else sumTo(t.thenApply(_ + n), n - 1)

    sumTo(CompletableFuture.completedFuture(0), depth)
      .get()
  }

  @Benchmark
  def monoMap(): BigInt = {
    import reactor.core.publisher.Mono

    @tailrec
    def sumTo(t: Mono[BigInt], n: Int): Mono[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    sumTo(Mono.fromCallable(() => 0), depth)
      .block()
  }

  @Benchmark
  def rxSingleMap(): BigInt = {
    import io.reactivex.Single

    @tailrec
    def sumTo(t: Single[BigInt], n: Int): Single[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    sumTo(Single.fromCallable(() => 0), depth)
      .blockingGet()
  }

  @Benchmark
  def twitterFutureMap(): BigInt = {
    import com.twitter.util.{ Await, Future }

    @tailrec
    def sumTo(t: Future[BigInt], n: Int): Future[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    Await.result(sumTo(Future.apply(0), depth))

  }

  @Benchmark
  def monixMap(): BigInt = {
    import monix.eval.Task

    @tailrec
    def sumTo(t: Task[BigInt], n: Int): Task[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    sumTo(Task.eval(0), depth).runSyncStep.right.get
  }

  @Benchmark
  def scalazMap(): BigInt = {
    @tailrec
    def sumTo(t: UIO[BigInt], n: Int): UIO[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    unsafeRun(sumTo(IO.succeedLazy(0), depth))
  }

  @Benchmark
  def catsMap(): BigInt = {
    import cats.effect._

    @tailrec
    def sumTo(t: IO[BigInt], n: Int): IO[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    sumTo(IO(0), depth).unsafeRunSync()
  }
}
