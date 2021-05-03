package zio.internal

import org.openjdk.jmh.annotations._
import zio.IOBenchmarks._
import zio.{State => _, _}

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
private[this] class ForeachPar_Benchmark {

  private val as = 1 to 10000

  @Benchmark
  def foreachPar_(): Unit =
    unsafeRun(ZIO.foreachPar_(as)(_ => ZIO.unit))

  @Benchmark
  def naiveForeachPar_(): Unit =
    unsafeRun(naiveForeachPar_(as)(_ => ZIO.unit))

  private def naiveForeachPar_[R, E, A](
    as: Iterable[A]
  )(f: A => ZIO[R, E, Any]): ZIO[R, E, Unit] =
    as.foldLeft(ZIO.unit: ZIO[R, E, Unit])((acc, a) => acc.zipParLeft(f(a))).refailWithTrace
}
