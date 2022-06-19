package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
private[this] class ForeachParDiscardBenchmark {

  private val as = 1 to 10000

  @Benchmark
  def foreachParDiscard(): Unit =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(ZIO.foreachParDiscard(as)(_ => ZIO.unit))
    }

  @Benchmark
  def naiveForeachParDiscard(): Unit =
    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(naiveForeachParDiscard(as)(_ => ZIO.unit))
    }

  private def naiveForeachParDiscard[R, E, A](
    as: Iterable[A]
  )(f: A => ZIO[R, E, Any]): ZIO[R, E, Unit] =
    as.foldLeft(ZIO.unit: ZIO[R, E, Unit])((acc, a) => acc.zipParLeft(f(a)))
}
