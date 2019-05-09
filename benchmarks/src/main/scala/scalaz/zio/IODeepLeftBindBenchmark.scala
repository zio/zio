package scalaz.zio

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import scalaz.zio.IOBenchmarks._

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class IODeepLeftBindBenchmark {
  @Param(Array("10000"))
  var depth: Int = _

  @Benchmark
  def monixDeepLeftBindBenchmark(): Int = {
    import monix.eval.Task

    var i = 0
    var io = Task.eval(i)
    while (i < depth) {
      io = io.flatMap(i => Task.eval(i))
      i += 1
    }

    io.runSyncStep.right.get
  }

  @Benchmark
  def scalazDeepLeftBindBenchmark(): Int = {
    var i = 0
    var io = IO.succeedLazy(i)
    while (i < depth) {
      io = io.flatMap(i => IO.succeedLazy(i))
      i += 1
    }

    unsafeRun(io)
  }

  @Benchmark
  def catsDeepLeftBindBenchmark(): Int = {
    import cats.effect.IO

    var i = 0
    var io = IO(i)
    while (i < depth) {
      io = io.flatMap(i => IO(i))
      i += 1
    }

    io.unsafeRunSync
  }

}
