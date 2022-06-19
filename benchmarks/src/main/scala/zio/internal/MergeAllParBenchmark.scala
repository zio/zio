package zio.internal

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._
import zio.ZIO.succeedNow
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1)
private[this] class MergeAllParBenchmark {

  private val in = List.fill(10000)(ZIO.unit)

  @Benchmark
  def mergeAllPar(): Unit =
    unsafeRun(ZIO.mergeAllPar(in)(())((_, _) => ()))

  @Benchmark
  def naiveMergeAllPar(): Unit =
    unsafeRun(naiveMergeAllPar(in)(())((_, _) => ()))

  private def naiveMergeAllPar[R, E, A, B](
    in: Iterable[ZIO[R, E, A]]
  )(zero: B)(f: (B, A) => B): ZIO[R, E, B] =
    in.foldLeft[ZIO[R, E, B]](succeedNow[B](zero))((acc, a) => acc.zipPar(a).map(f.tupled))
}
