package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit
import scala.concurrent.TimeoutException

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

  @Benchmark
  def zioBaseline = {
    val _ = unsafeRun {
      ZIO.unit
    }
  }

  @Benchmark
  def zioTimeout = {
    val _ = unsafeRun {
      ZIO
        .unit
        .timeoutFail(new TimeoutException)(100.minutes)
    }
  }

  @Benchmark
  def zioTimeout1 = {
    val _ = unsafeRun {
      ZIO
        .unit
        .timeoutTo(ZIO.fail(new TimeoutException))
        .apply1(ZIO.succeed(_))(100.minutes)
        .flatten
    }
  }



}
