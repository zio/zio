package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

/**
 * {{{
 * [info] Benchmark                    (depth)   Mode  Cnt        Score       Error  Units
 * [info] UnsafeRunBenchmark.zioLeft         1  thrpt    5  2527835.063 ∩┐╜ 20547.193  ops/s
 * [info] UnsafeRunBenchmark.zioLeft         2  thrpt    5  2192859.126 ∩┐╜ 21557.848  ops/s
 * [info] UnsafeRunBenchmark.zioLeft         4  thrpt    5  1861759.677 ∩┐╜ 21811.809  ops/s
 * [info] UnsafeRunBenchmark.zioLeft         8  thrpt    5  1478009.404 ∩┐╜ 13764.737  ops/s
 * [info] UnsafeRunBenchmark.zioRight        1  thrpt    5  2434178.854 ∩┐╜ 23297.673  ops/s
 * [info] UnsafeRunBenchmark.zioRight        2  thrpt    5  2197319.901 ∩┐╜ 13466.396  ops/s
 * [info] UnsafeRunBenchmark.zioRight        4  thrpt    5  1981274.470 ∩┐╜ 15435.194  ops/s
 * [info] UnsafeRunBenchmark.zioRight        8  thrpt    5  1561479.601 ∩┐╜ 42674.608  ops/s
 * }}}
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class UnsafeRunBenchmark {
  import BenchmarkUtil.unsafeRun

  var leftZio: UIO[Any]            = _
  var leftCio: cats.effect.IO[Any] = _

  var rightZio: UIO[Any]            = _
  var rightCio: cats.effect.IO[Any] = _

  @Param(Array("1", "2", "4", "8"))
  var depth: Int = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    def makeLeftZio(n: Int): UIO[Int] =
      if (n <= 1) UIO(0)
      else makeLeftZio(n - 1).flatMap(m => ZIO.succeed(n + m))

    def makeRightZio(n: Int, acc: Int = 0): UIO[Int] =
      if (n <= 1) UIO(acc)
      else ZIO.succeed(n).flatMap(n => makeRightZio(n - 1, acc + n))

    def makeLeftCio(n: Int): cats.effect.IO[Int] =
      if (n <= 1) cats.effect.IO(0)
      else makeLeftCio(n - 1).flatMap(m => cats.effect.IO(n + m))

    def makeRightCio(n: Int, acc: Int = 0): cats.effect.IO[Int] =
      if (n <= 1) cats.effect.IO(acc)
      else cats.effect.IO(n).flatMap(n => makeRightCio(n - 1, acc + n))

    leftZio = makeLeftZio(depth)
    rightZio = makeRightZio(depth)
    leftCio = makeLeftCio(depth)
    rightCio = makeRightCio(depth)
  }

  @Benchmark
  def zioLeft(): Unit = {
    val _ = unsafeRun(leftZio)
  }

  @Benchmark
  def zioRight(): Unit = {
    val _ = unsafeRun(rightZio)
  }

  @Benchmark
  def cioLeft(): Unit = {
    val _ = leftCio.unsafeRunSync()
  }

  @Benchmark
  def cioRight(): Unit = {
    val _ = rightCio.unsafeRunSync()
  }
}
