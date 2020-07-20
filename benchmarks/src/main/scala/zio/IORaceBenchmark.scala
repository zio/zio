package zio

import java.util.concurrent.TimeUnit

import cats.effect.{IO => CIO}
import monix.eval.{Task => MTask}
import org.openjdk.jmh.annotations._
import zio.IOBenchmarks._

import scala.annotation.tailrec

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 5, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 3)
@Fork(3)
class IORaceBenchmark {
  @Param(Array("20"))
  var depth: Int = _

  @Benchmark
  def monix(): Either[BigInt, BigInt]  = {
    @tailrec
    def sumTo(t: MTask[BigInt], n: Int): MTask[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    MTask.race(sumTo(MTask.eval(0), depth), sumTo(MTask.eval(0), depth)).runSyncUnsafe()
  }

  @Benchmark
  def zio(): Either[BigInt, BigInt]  = zio(IOBenchmarks)

  @Benchmark
  def zioTraced(): Either[BigInt, BigInt]  = zio(TracedRuntime)

  private[this] def zio(runtime: Runtime[Any]): Either[BigInt, BigInt] = {
    @tailrec
    def sumTo(t: UIO[BigInt], n: Int): UIO[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    runtime.unsafeRun(sumTo(IO.effectTotal(0), depth) raceEither sumTo(IO.effectTotal(0), depth))
  }

  @Benchmark
  def cats(): Either[BigInt, BigInt] = {

    @tailrec
    def sumTo(t: CIO[BigInt], n: Int): CIO[BigInt] =
      if (n <= 1) t
      else sumTo(t.map(_ + n), n - 1)

    CIO.race(sumTo(CIO(0), depth), sumTo(CIO(0), depth)).unsafeRunSync()
  }
}
