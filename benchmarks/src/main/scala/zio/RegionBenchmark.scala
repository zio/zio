package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

/**
 * {{{
 *
 * [info] Benchmark                    (nesting)   Mode  Cnt      Score     Error  Units
 * [info] RegionBenchmark.catsBracket        100  thrpt    5  38666.762 ∩┐╜ 268.038  ops/s
 * [info] RegionBenchmark.zioBracket         100  thrpt    5  34889.861 ∩┐╜ 116.706  ops/s
 *
 * [info] Benchmark                   (nesting)   Mode  Cnt     Score    Error  Units
 * [info] RegionBenchmark.zioProvide       1000  thrpt    5  9624.029 ∩┐╜ 52.489  ops/s
 *
 * [info] Benchmark                            (nesting)   Mode  Cnt       Score      Error  Units
 * [info] RegionBenchmark.catsUninterruptible        100  thrpt    5   98229.449 ∩┐╜ 4655.877  ops/s
 * [info] RegionBenchmark.zioUninterruptible         100  thrpt    5  317538.363 ∩┐╜ 7098.315  ops/s
 *
 * [info] Benchmark                            (nesting)   Mode  Cnt      Score      Error  Units
 * [info] RegionBenchmark.catsUninterruptibleMask    100  thrpt    5  57568.159 ∩┐╜ 581.876  ops/s
 * [info] RegionBenchmark.zioUninterruptibleMask     100  thrpt    5  88252.574 ∩┐╜ 575.740  ops/s
 *
 * [info] Benchmark                            (nesting)   Mode  Cnt      Score      Error  Units
 * [info] RegionBenchmark.zioUninterruptible2       1000  thrpt    5  20062.071 ∩┐╜ 277.925  ops/s
 * }}}
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Threads(16)
@Fork(1)
class RegionBenchmark {
  import BenchmarkUtil.unsafeRun

  @Param(Array("100"))
  var nesting: Int = _

  @Benchmark
  def zioEnsuring(): Unit = {
    def nest(n: Int, uio: UIO[Unit]): UIO[Unit] =
      if (n <= 1) uio
      else ZIO.suspendSucceed(nest(n - 1, uio)).ensuring(ZIO.unit)

    val _ = unsafeRun(nest(nesting, ZIO.unit))
  }

  @Benchmark
  def zioBracket(): Unit = {
    def nest(n: Int, uio: UIO[Unit]): UIO[Unit] =
      if (n <= 1) uio
      else ZIO.acquireReleaseWith(ZIO.unit)(_ => ZIO.unit)(_ => nest(n - 1, uio))

    val _ = unsafeRun(nest(nesting, ZIO.unit))
  }

  @Benchmark
  def zioProvideEnvironment(): Unit = {
    def nest(n: Int, uio: UIO[Unit]): UIO[Unit] =
      if (n <= 1) uio
      else (ZIO.suspendSucceed(nest(n - 1, uio)): ZIO[Unit, Nothing, Unit]).provideEnvironment(ZEnvironment(()))

    val _ = unsafeRun(nest(nesting, ZIO.unit))
  }

  @Benchmark
  def zioUninterruptible(): Unit = {
    def nest(n: Int, uio: UIO[Unit]): UIO[Unit] =
      if (n <= 1) uio
      else ZIO.suspendSucceed(nest(n - 1, uio)).uninterruptible

    val _ = unsafeRun(nest(nesting, ZIO.unit))
  }

  @Benchmark
  def zioUninterruptible2(): Unit = {
    def nest(n: Int, uio: UIO[Unit]): UIO[Unit] =
      if (n <= 1) uio
      else {
        val effect = ZIO.suspendSucceed(nest(n - 1, uio))

        if ((n % 2) == 0) effect.uninterruptible
        else effect.interruptible
      }

    val _ = unsafeRun(nest(nesting, ZIO.unit))
  }

  @Benchmark
  def zioUninterruptibleMask(): Unit = {
    def nest(n: Int, uio: UIO[Unit]): UIO[Unit] =
      if (n <= 1) uio
      else ZIO.suspendSucceed(ZIO.uninterruptibleMask(restore => ZIO.unit *> restore(nest(n - 1, uio))))

    val _ = unsafeRun(nest(nesting, ZIO.unit))
  }

  @Benchmark
  def catsUninterruptibleMask(): Unit = {
    import cats.effect._
    import cats.effect.unsafe.implicits.global

    def nest(n: Int, uio: IO[Unit]): IO[Unit] =
      if (n <= 1) uio
      else IO.defer(IO.uncancelable(restore => IO.unit *> restore(nest(n - 1, uio))))

    val _ = nest(nesting, IO.unit).unsafeRunSync()
  }

  @Benchmark
  def catsEnsuring(): Unit = {
    import cats.effect._
    import cats.effect.unsafe.implicits.global

    def nest(n: Int, uio: IO[Unit]): IO[Unit] =
      if (n <= 1) uio
      else IO.defer(nest(n - 1, uio)).guarantee(IO.unit)

    val _ = nest(nesting, IO.unit).unsafeRunSync()
  }

  @Benchmark
  def catsBracket(): Unit = {
    import cats.effect._
    import cats.effect.unsafe.implicits.global

    def nest(n: Int, uio: IO[Unit]): IO[Unit] =
      if (n <= 1) uio
      else IO.unit.bracket(_ => nest(n - 1, uio))(_ => IO.unit)

    val _ = nest(nesting, IO.unit).unsafeRunSync()
  }

  @Benchmark
  def catsUninterruptible(): Unit = {
    import cats.effect._
    import cats.effect.unsafe.implicits.global

    def nest(n: Int, uio: IO[Unit]): IO[Unit] =
      if (n <= 1) uio
      else IO.defer(nest(n - 1, uio)).uncancelable

    val _ = nest(nesting, IO.unit).unsafeRunSync()
  }

}
