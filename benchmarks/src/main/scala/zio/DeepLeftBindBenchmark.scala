package zio

import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class DeepLeftBindBenchmark {
  @Param(Array("10000"))
  var depth: Int = _

  @Benchmark
  def zioDeepLeftBindBenchmark(): Int = zioDeepLeftBindBenchmark(BenchmarkUtil)

  def zioDeepLeftBindBenchmark(runtime: Runtime[Any]): Int = {
    var i  = 0
    var io = ZIO.succeed(i)
    while (i < depth) {
      io = io.flatMap(i => ZIO.succeed(i))
      i += 1
    }

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(io).getOrThrowFiberFailure()
    }
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
