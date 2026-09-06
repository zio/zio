package zio

import org.openjdk.jmh.annotations.{Scope => JScope, _}

import java.util.concurrent.TimeUnit

/**
 * Measures binding over an effect that is already a value.
 *
 * `Ref#get` is `ZIO.succeed(unsafe.get(..))` ascribed to `UIO[A]`, so the
 * static type hides the fact that the runtime node is a `Sync`. Binding on it
 * therefore builds a `FlatMap` whose `first` is already a value -- the shape
 * that dominates ordinary `Ref`-based application code:
 *
 * {{{
 *   for {
 *     a <- refA.get
 *     b <- refB.get
 *     _ <- refA.set(a + b)
 *   } yield ()
 * }}}
 *
 * `@OperationsPerInvocation` normalises each score by that benchmark's own unit
 * of work rather than reporting throughput of the whole loop. Note the units
 * differ: `refGetFlatMap` and `refUpdateGet` divide by binds (one per
 * iteration), while `refGetChained` divides by `Ref` reads (three per
 * iteration, alongside a `map` and the loop's own bind that are not counted).
 * Each benchmark is therefore comparable against itself across runs, but the
 * three are not on a common scale with one another.
 */
@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class RefGetBenchmark {
  import BenchmarkUtil.unsafeRun
  import RefGetBenchmark._

  var ref: Ref[Int] = _

  // Reset per iteration, not per trial, so every iteration starts from the
  // same state rather than from wherever the preceding iterations left the
  // counter. `refUpdateGet` boxes an `Integer` per update, and values in
  // [-128, 127] come from the JVM's cache and allocate nothing, so a fixed
  // starting point keeps that (small, ~13% at n=1000) effect identical across
  // iterations and across both arms of an A/B.
  @Setup(Level.Iteration)
  def setup(): Unit =
    ref = unsafeRun(Ref.make(0))

  /** One bind over a `Ref#get`, repeated: the isolated per-bind cost. */
  @Benchmark
  @OperationsPerInvocation(size)
  def refGetFlatMap(): Int = {
    def loop(i: Int, acc: Int): UIO[Int] =
      if (i >= size) Exit.succeed(acc)
      else ref.get.flatMap(v => loop(i + 1, acc + v))

    unsafeRun(loop(0, 0))
  }

  /**
   * A for-comprehension over several `Ref` reads, as application code writes
   * it. Normalised per `Ref` read: each iteration performs 3 of them (which
   * desugar to two `flatMap`s over a `Sync` and one `map`).
   */
  @Benchmark
  @OperationsPerInvocation(size * 3)
  def refGetChained(): Int = {
    def step: UIO[Int] =
      for {
        a <- ref.get
        b <- ref.get
        c <- ref.get
      } yield a + b + c

    def loop(i: Int, acc: Int): UIO[Int] =
      if (i >= size) Exit.succeed(acc)
      else step.flatMap(v => loop(i + 1, acc + v))

    unsafeRun(loop(0, 0))
  }

  /** `update` then bind: the common read-modify-write shape. */
  @Benchmark
  @OperationsPerInvocation(size)
  def refUpdateGet(): Int = {
    def loop(i: Int): UIO[Int] =
      if (i >= size) ref.get
      else ref.update(_ + 1).flatMap(_ => loop(i + 1))

    unsafeRun(loop(0))
  }
}

object RefGetBenchmark {

  /**
   * Must be a compile-time constant so it can be used in
   * `@OperationsPerInvocation`.
   */
  final val size = 1000
}
