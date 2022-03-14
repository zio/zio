package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

/**
 * {{{
 * [info] UnsafeRunBenchmark.zioLeft          1  thrpt   10    2924072.908 ∩┐╜   207535.037  ops/s
 * [info] UnsafeRunBenchmark.zioLeft'         1  thrpt   10  264234984.929 ∩┐╜ 27407427.777  ops/s
 *
 * [info] UnsafeRunBenchmark.zioLeft          2  thrpt   10    2619085.083 ∩┐╜   181905.105  ops/s
 * [info] UnsafeRunBenchmark.zioLeft'         2  thrpt   10  109613072.462 ∩┐╜   526748.448  ops/s
 *
 * [info] UnsafeRunBenchmark.zioLeft          4  thrpt   10    2252902.513 ∩┐╜    71522.781  ops/s
 * [info] UnsafeRunBenchmark.zioLeft'         4  thrpt   10   44569924.478 ∩┐╜  1060326.379  ops/s
 *
 * [info] UnsafeRunBenchmark.zioLeft          8  thrpt   10    1782754.491 ∩┐╜    59228.031  ops/s
 * [info] UnsafeRunBenchmark.zioLeft'         8  thrpt   10   15689424.820 ∩┐╜  1487690.562  ops/s
 *
 * [info] UnsafeRunBenchmark.zioRight         1  thrpt   10    2673482.726 ∩┐╜    24960.009  ops/s
 * [info] UnsafeRunBenchmark.zioRight'        1  thrpt   10  270720926.073 ∩┐╜   537049.199  ops/s
 *
 * [info] UnsafeRunBenchmark.zioRight         2  thrpt   10    2644805.378 ∩┐╜    58712.959  ops/s
 * [info] UnsafeRunBenchmark.zioRight'        2  thrpt   10   98828477.442 ∩┐╜   493812.270  ops/s
 *
 * [info] UnsafeRunBenchmark.zioRight         4  thrpt   10    2434308.062 ∩┐╜    22470.067  ops/s
 * [info] UnsafeRunBenchmark.zioRight'        4  thrpt   10   35925377.327 ∩┐╜  1732444.668  ops/s
 *
 * [info] UnsafeRunBenchmark.zioRight         8  thrpt   10    2035601.943 ∩┐╜    69795.749  ops/s
 * [info] UnsafeRunBenchmark.zioRight'        8  thrpt   10   15740140.258 ∩┐╜   672817.959  ops/s
 * }}}
 */
@State(JScope.Thread)
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
      if (n <= 1) ZIO.succeed(0)
      else makeLeftZio(n - 1).flatMap(m => ZIO.succeed(n + m))

    def makeRightZio(n: Int, acc: Int = 0): UIO[Int] =
      if (n <= 1) ZIO.succeed(acc)
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
