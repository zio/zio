package zio.internal

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio.IOBenchmarks._
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
private[this] class ReduceAllParBenchmark {

  private val a  = ZIO.unit
  private val as = List.fill(10000)(ZIO.unit)

  @Benchmark
  def reduceAllPar(): Unit =
    unsafeRun(ZIO.reduceAllPar(a, as)((_, _) => ()))

  @Benchmark
  def naiveReduceAllPar(): Unit =
    unsafeRun(naiveReduceAllPar(a, as)((_, _) => ()))

  def naiveReduceAllPar[R, R1 <: R, E, A](a: ZIO[R, E, A], as: Iterable[ZIO[R1, E, A]])(
    f: (A, A) => A
  ): ZIO[R1, E, A] =
    as.foldLeft[ZIO[R1, E, A]](a)((l, r) => l.zipPar(r).map(f.tupled))
}
