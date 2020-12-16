package zio.stacktracer

import org.openjdk.jmh.annotations._
import zio.internal.stacktracer.impl.{ AkkaLineNumbers, AkkaLineNumbersTracer }
import zio.stacktracer.TracersBenchmark.{ akkaTracer, asmTracer }
import zio.stacktracer.impls.AsmTracer

import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 20, time = 1)
@Measurement(iterations = 20, time = 1)
@Fork(1)
class TracersBenchmark {

  @Benchmark
  def akkaLineNumbers1000Lambdas(): Unit = {
    var i = 0
    while (i != 1000) {
      AkkaLineNumbers((a: Any) => a)
      i += 1
    }
  }

  @Benchmark
  def akkaTracer1000Lambdas(): Unit = {
    var i = 0
    while (i != 1000) {
      akkaTracer.traceLocation((a: Any) => a)
      i += 1
    }
  }

  @Benchmark
  def asmTracer1000Lambdas(): Unit = {
    var i = 0
    while (i != 1000) {
      asmTracer.traceLocation((a: Any) => a)
      i += 1
    }
  }

}

object TracersBenchmark {
  val akkaTracer = new AkkaLineNumbersTracer
  val asmTracer  = new AsmTracer
}
