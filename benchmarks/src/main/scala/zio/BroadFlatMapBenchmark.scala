package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit
import scala.concurrent.Await

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Threads(16)
@Fork(1)
class BroadFlatMapBenchmark {
  @Param(Array("20"))
  var depth: Int = _

  @Benchmark
  def futureBroadFlatMap(): BigInt = {
    import scala.concurrent.Future
    import scala.concurrent.duration.Duration.Inf

    def fib(n: Int): Future[BigInt] =
      if (n <= 1) Future(n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => Future(a + b)))

    Await.result(fib(depth), Inf)
  }

  @Benchmark
  def completableFutureBroadFlatMap(): BigInt = {
    import java.util.concurrent.CompletableFuture

    def fib(n: Int): CompletableFuture[BigInt] =
      if (n <= 1) CompletableFuture.completedFuture(n)
      else
        fib(n - 1).thenCompose(a => fib(n - 2).thenCompose(b => CompletableFuture.completedFuture(a + b)))

    fib(depth)
      .get()
  }

  @Benchmark
  def twitterBroadFlatMap(): BigInt = {
    import com.twitter.util.{Await, Future}

    def fib(n: Int): Future[BigInt] =
      if (n <= 1) Future(n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => Future(a + b)))

    Await.result(fib(depth))
  }

  @Benchmark
  def zioBroadFlatMap(): BigInt = zioBroadFlatMap(BenchmarkUtil)

  private[this] def zioBroadFlatMap(runtime: Runtime[Any]): BigInt = {
    def fib(n: Int): UIO[BigInt] =
      if (n <= 1) ZIO.succeed[BigInt](n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => ZIO.succeed(a + b)))

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(fib(depth)).getOrThrowFiberFailure()
    }
  }

  @Benchmark
  def zioBroadFlatMapTracePropagation(): BigInt = zioBroadFlatMapTracePropagation(BenchmarkUtil)

  private[this] def zioBroadFlatMapTracePropagation(runtime: Runtime[Any]): BigInt = {
    def fib(n: Int)(implicit trace: Trace): UIO[BigInt] =
      if (n <= 1) ZIO.succeed[BigInt](n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => ZIO.succeed(a + b)))

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(fib(depth)).getOrThrowFiberFailure()
    }
  }

  @Benchmark
  def catsBroadFlatMap(): BigInt = {
    import cats.effect._

    def fib(n: Int): IO[BigInt] =
      if (n <= 1) IO(n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => IO(a + b)))

    fib(depth).unsafeRunSync()
  }
}
