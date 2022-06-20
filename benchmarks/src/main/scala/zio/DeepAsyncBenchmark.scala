package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration.Inf

/*

[info] Benchmark                               (depth)   Mode  Cnt      Score      Error  Units
[info] DeepAsyncBenchmark.catsDeepAsyncMany         50  thrpt   10  53181.287 ±  944.142  ops/s
[info] DeepAsyncBenchmark.catsDeepAsyncOne          50  thrpt   10  75764.041 ± 1522.090  ops/s
[info] DeepAsyncBenchmark.futureDeepAsyncMany       50  thrpt   10  10715.597 ±  452.715  ops/s
[info] DeepAsyncBenchmark.futureDeepAsyncOne        50  thrpt   10  32877.804 ± 1751.663  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncMany          50  thrpt   10  19196.619 ±  485.231  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncOne           50  thrpt   10  38472.168 ±  735.377  ops/s

GROWABLE ARRAY

[info] Benchmark                               (depth)   Mode  Cnt      Score      Error  Units
[info] DeepAsyncBenchmark.catsDeepAsyncMany         50  thrpt   10  52751.510 ± 1348.517  ops/s
[info] DeepAsyncBenchmark.catsDeepAsyncOne          50  thrpt   10  74366.768 ± 2416.521  ops/s
[info] DeepAsyncBenchmark.futureDeepAsyncMany       50  thrpt   10  10297.019 ± 1669.367  ops/s
[info] DeepAsyncBenchmark.futureDeepAsyncOne        50  thrpt   10  24926.170 ± 7356.075  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncMany          50  thrpt   10  24288.439 ± 1061.643  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncOne           50  thrpt   10  38537.448 ± 1570.877  ops/s

REUSE GROWABLE ARRAY

[info] Benchmark                               (depth)   Mode  Cnt      Score      Error  Units
[info] DeepAsyncBenchmark.catsDeepAsyncMany         50  thrpt   10  51275.253 ±  927.356  ops/s
[info] DeepAsyncBenchmark.catsDeepAsyncOne          50  thrpt   10  77765.254 ± 1150.578  ops/s
[info] DeepAsyncBenchmark.futureDeepAsyncMany       50  thrpt   10  10605.806 ±  770.572  ops/s
[info] DeepAsyncBenchmark.futureDeepAsyncOne        50  thrpt   10  33811.533 ± 1987.224  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncMany          50  thrpt   10  29901.062 ±  286.237  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncOne           50  thrpt   10  53428.394 ±  502.145  ops/s

MUTABLE ASYNC STATUS

[info] Benchmark                               (depth)   Mode  Cnt      Score      Error  Units
[info] DeepAsyncBenchmark.catsDeepAsyncMany         50  thrpt   10  45563.829 ± 4984.766  ops/s
[info] DeepAsyncBenchmark.catsDeepAsyncOne          50  thrpt   10  79798.669 ± 1374.365  ops/s
[info] DeepAsyncBenchmark.futureDeepAsyncMany       50  thrpt   10  10718.747 ± 1025.896  ops/s
[info] DeepAsyncBenchmark.futureDeepAsyncOne        50  thrpt   10  30636.797 ± 3881.584  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncMany          50  thrpt   10  30047.426 ± 362.773  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncOne           50  thrpt   10  54828.353 ± 511.713  ops/s

FULLY FIBER HEAP-BASED AYSNC STATUS

[info] Benchmark                            (depth)   Mode  Cnt      Score      Error  Units
[info] DeepAsyncBenchmark.zioDeepAsyncMany       50  thrpt   10  29352.500 ± 1078.870  ops/s
[info] DeepAsyncBenchmark.zioDeepAsyncOne        50  thrpt   10  54886.982 ±  429.813  ops/s

 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
class DeepAsyncBenchmark {
  @Param(Array("50"))
  var depth: Int = _

  @Benchmark
  def futureDeepAsyncOne(): Int = {
    def recurse(n: Int): Future[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => Future.successful(i + 1))
      else {
        val p = scala.concurrent.Promise[Int]()
        Future(p.success(0))
        p.future
      }

    Await.result(recurse(depth), Inf)
  }

  @Benchmark
  def futureDeepAsyncMany(): Int = {
    def recurse(n: Int): Future[Int] =
      if (n > 0) recurse(n - 1).flatMap { i =>
        val p = scala.concurrent.Promise[Int]()
        Future(p.success(i + 1))
        p.future
      }
      else {
        val p = scala.concurrent.Promise[Int]()
        Future(p.success(0))
        p.future
      }

    Await.result(recurse(depth), Inf)
  }

  @Benchmark
  def catsDeepAsyncOne(): Int = {
    import cats.effect._

    def recurse(n: Int): IO[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => IO(i + 1))
      else IO.async_[Int](k => k(Right(0)))

    recurse(depth).unsafeRunSync()
  }

  @Benchmark
  def catsDeepAsyncMany(): Int = {
    import cats.effect._

    def recurse(n: Int): IO[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => IO.async_(k => k(Right(i + 1))))
      else IO.async_[Int](k => k(Right(0)))

    recurse(depth).unsafeRunSync()
  }

  @Benchmark
  def zioDeepAsyncOne(): Int = {
    import zio.BenchmarkUtil._

    def recurse(n: Int): UIO[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => ZIO.succeed(i + 1))
      else ZIO.async[Any, Nothing, Int](k => k(ZIO.succeedNow(0)))

    unsafeRun(recurse(depth))
  }

  @Benchmark
  def zioDeepAsyncMany(): Int = {
    import zio.BenchmarkUtil._

    def recurse(n: Int): UIO[Int] =
      if (n > 0) recurse(n - 1).flatMap(i => ZIO.async[Any, Nothing, Int](k => k(ZIO.succeedNow(i + 1))))
      else ZIO.async[Any, Nothing, Int](k => k(ZIO.succeedNow(0)))

    unsafeRun(recurse(depth))
  }
}
