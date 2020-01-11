package zio.internal

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import zio.IOBenchmarks._
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
private[this] class ForeachParN_Benchmark {

  private val n: Int = 8

  private val as = 1 to 10000

  @Benchmark
  def foreachParN_(): Unit =
    unsafeRun(ZIO.foreachParN_(n)(as)(_ => ZIO.unit))

  @Benchmark
  def naiveForeachParN_(): Unit =
    unsafeRun(naiveForeachParN_(n)(as)(_ => ZIO.unit))

  private def naiveForeachPar_[R, E, A](
    as: Iterable[A]
  )(f: A => ZIO[R, E, Any]): ZIO[R, E, Unit] =
    as.foldLeft(ZIO.unit: ZIO[R, E, Unit]) { (acc, a) =>
        acc.zipParLeft(f(a))
      }
      .refailWithTrace

  private def naiveForeachParN_[R, E, A](
    n: Int
  )(as: Iterable[A])(f: A => ZIO[R, E, Any]): ZIO[R, E, Unit] =
    Semaphore
      .make(n.toLong)
      .flatMap { semaphore =>
        naiveForeachPar_(as) { a =>
          semaphore.withPermit(f(a))
        }
      }
      .refailWithTrace
}
