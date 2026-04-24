package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class AwaitBenchmark {
  import BenchmarkUtil.unsafeRun

  @Param(Array("10000"))
  var n: Int = _

  var range: List[Int] = _

  @Setup(Level.Trial)
  def setup(): Unit =
    range = (0 to n).toList

  @Benchmark
  def zioAwait(): Unit = {
    val _ =
      unsafeRun(
        for {
          fiber <- ZIO.succeed(42).fork
          _     <- ZIO.foreachDiscard(range)(_ => fiber.await)
        } yield ()
      )
  }

  @Benchmark
  def catsAwait(): Unit = {
    import cats.effect._
    import BenchmarkUtil._

    val _ =
      (for {
        fiber <- IO(42).start
        _     <- catsForeachDiscard(range)(_ => fiber.join)
      } yield ()).unsafeRunSync()
  }

}
