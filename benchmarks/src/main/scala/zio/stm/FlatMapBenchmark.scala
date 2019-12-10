package zio.stm

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import zio._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class FlatMapBenchmark {
  @Param(Array("20"))
  var depth: Int = _

  @Benchmark
  def deepFlatMap(runtime: Runtime[Any]): BigInt = {
    def fib(n: Int): STM[Nothing, BigInt] =
      if (n <= 1) STM.succeed[BigInt](n)
      else
        fib(n - 1).flatMap { a =>
          fib(n - 2).flatMap(b => STM.succeed(a + b))
        }

    runtime.unsafeRun(fib(depth).commit)
  }
}
