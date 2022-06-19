package zio.stm

import cats.effect.unsafe.implicits.global
import cats.effect.{IO => CIO}
import io.github.timwspence.cats.stm.{STM => CatsSTM}
import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Warmup(iterations = 15, timeUnit = TimeUnit.SECONDS, time = 10)
@Fork(1)
class STMFlatMapBenchmark {
  import BenchmarkUtil._

  @Param(Array("20"))
  var depth: Int = _

  val catsSTM: CatsSTM[CIO] = CatsSTM.runtime[CIO].unsafeRunSync()
  import catsSTM._

  @Benchmark
  def catsFlatMap(): BigInt = {
    def fib(n: Int): Txn[BigInt] =
      if (n <= 1) catsSTM.pure[BigInt](n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => catsSTM.pure(a + b)))

    catsSTM.commit(fib(depth)).unsafeRunSync()
  }

  @Benchmark
  def zioFlatMap(): BigInt = {
    def fib(n: Int): STM[Nothing, BigInt] =
      if (n <= 1) STM.succeedNow[BigInt](n)
      else
        fib(n - 1).flatMap(a => fib(n - 2).flatMap(b => STM.succeedNow(a + b)))

    Unsafe.unsafeCompat { implicit u =>
      unsafeRun(fib(depth).commit)
    }
  }
}
