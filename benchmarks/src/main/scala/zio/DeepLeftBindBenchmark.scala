package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations._
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class DeepLeftBindBenchmark {
  @Param(Array("10000"))
  var depth: Int = _

  @Benchmark
  def zioDeepLeftBindBenchmark(): Int = zioDeepLeftBindBenchmark(BenchmarkUtil)

  @Benchmark
  def zioTracedDeepLeftBindBenchmark(): Int = zioDeepLeftBindBenchmark(TracedRuntime)

  def zioDeepLeftBindBenchmark(runtime: Runtime[Any]): Int = {
    var i  = 0
    var io = IO.succeed(i)
    while (i < depth) {
      io = io.flatMap(i => IO.succeed(i))
      i += 1
    }

    runtime.unsafeRun(io)
  }

  @Benchmark
  def catsDeepLeftBindBenchmark(): Int = {
    import cats.effect.IO

    var i  = 0
    var io = IO(i)
    while (i < depth) {
      io = io.flatMap(i => IO(i))
      i += 1
    }

    io.unsafeRunSync()
  }

}
