package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import zio.BenchmarkUtil._

import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class PromiseBecomeBenchmark {

  @Param(Array("1000", "10000", "100000"))
  var n: Int = _

  /**
   * Traditional pattern: fork a fiber, flatMap the result to complete the
   * promise, then await the promise. This creates extra allocations and
   * indirection.
   */
  @Benchmark
  def traditionalForkAwaitPromise(): Unit = {
    val io = Promise
      .make[Nothing, Int]
      .flatMap { promise =>
        ZIO.succeed(42).fork.flatMap { fiber =>
          fiber.await.flatMap(exit => promise.done(exit)).fork *> promise.await
        }
      }
      .repeatN(n)

    unsafeRun(io)
  }

  /**
   * Optimized pattern using Promise.become: fork a fiber, link the promise
   * directly to the fiber, then await the promise. This avoids the extra fiber
   * + flatMap overhead.
   */
  @Benchmark
  def promiseBecomeForkAwait(): Unit = {
    val io = Promise
      .make[Nothing, Int]
      .flatMap { promise =>
        ZIO.succeed(42).fork.flatMap { fiber =>
          promise.become(fiber) *> promise.await
        }
      }
      .repeatN(n)

    unsafeRun(io)
  }
}
