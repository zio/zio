import zio._
import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

/**
 * Benchmark to verify if the optimized race implementation solves the performance issue
 * mentioned in the bounty. This benchmark compares:
 * 1. Original ZIO race implementation
 * 2. Optimized ZIO race implementation
 * 3. Cats-effect race implementation
 *
 * The goal is to confirm if we've successfully addressed the 5x performance gap.
 */
@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
class RaceOptimizationVerificationBenchmark {
  @Param(Array("100", "1000", "10000"))
  var iterations: Int = _

  /**
   * Benchmark for cats-effect race implementation
   */
  @Benchmark
  def catsEffectRace(): Int = {
    import cats.effect.IO

    def loop(i: Int): IO[Int] =
      if (i < iterations) IO.race(IO.never, IO.delay(i + 1)).flatMap(_ => loop(i + 1))
      else IO.pure(i)

    loop(0).unsafeRunSync()
  }

  /**
   * Benchmark for original ZIO race implementation
   */
  @Benchmark
  def originalZioRace(): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < iterations) {
        // Use the standard race implementation directly from ZIO
        ZIO.never.race(ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      } else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
  }

  /**
   * Benchmark for optimized ZIO race implementation
   */
  @Benchmark
  def optimizedZioRace(): Int = {
    def loop(i: Int): UIO[Int] =
      if (i < iterations) {
        // Use the optimized race implementation
        implicit val trace = Trace.empty
        OptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      } else ZIO.succeed(i)

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
  }

  /**
   * Simple test to verify the benchmark works correctly
   */
  def main(args: Array[String]): Unit = {
    iterations = 1000
    println("Running verification test...")
    
    val start1 = System.nanoTime()
    val result1 = catsEffectRace()
    val time1 = System.nanoTime() - start1
    println(s"Cats-Effect race: $result1 in ${time1/1000000.0} ms")
    
    val start2 = System.nanoTime()
    val result2 = originalZioRace()
    val time2 = System.nanoTime() - start2
    println(s"Original ZIO race: $result2 in ${time2/1000000.0} ms")
    
    val start3 = System.nanoTime()
    val result3 = optimizedZioRace()
    val time3 = System.nanoTime() - start3
    println(s"Optimized ZIO race: $result3 in ${time3/1000000.0} ms")
    
    println("\nPerformance ratios:")
    println(s"Cats-Effect / Original ZIO = ${time1.toDouble / time2.toDouble}x")
    println(s"Cats-Effect / Optimized ZIO = ${time1.toDouble / time3.toDouble}x")
    println(s"Original ZIO / Optimized ZIO = ${time2.toDouble / time3.toDouble}x")
  }
}

/**
 * Simple application to run the benchmark without JMH
 */
object RaceOptimizationVerificationApp extends App {
  val benchmark = new RaceOptimizationVerificationBenchmark()
  benchmark.iterations = 10000
  
  println("Running performance verification...")
  println(s"Iterations: ${benchmark.iterations}")
  
  // Warmup
  println("\nWarmup:")
  benchmark.catsEffectRace()
  benchmark.originalZioRace()
  benchmark.optimizedZioRace()
  
  // Actual test
  println("\nBenchmark:")
  
  val start1 = System.nanoTime()
  val result1 = benchmark.catsEffectRace()
  val time1 = System.nanoTime() - start1
  println(s"Cats-Effect race: $result1 in ${time1/1000000.0} ms")
  
  val start2 = System.nanoTime()
  val result2 = benchmark.originalZioRace()
  val time2 = System.nanoTime() - start2
  println(s"Original ZIO race: $result2 in ${time2/1000000.0} ms")
  
  val start3 = System.nanoTime()
  val result3 = benchmark.optimizedZioRace()
  val time3 = System.nanoTime() - start3
  println(s"Optimized ZIO race: $result3 in ${time3/1000000.0} ms")
  
  println("\nPerformance ratios:")
  println(s"Cats-Effect / Original ZIO = ${time1.toDouble / time2.toDouble}x")
  println(s"Cats-Effect / Optimized ZIO = ${time1.toDouble / time3.toDouble}x")
  println(s"Original ZIO / Optimized ZIO = ${time2.toDouble / time3.toDouble}x")
  
  if (time1.toDouble / time3.toDouble >= 5.0) {
    println("\nVerification SUCCESSFUL: The optimized race implementation achieves the 5x performance improvement goal!")
  } else {
    println("\nVerification INCOMPLETE: The optimized race implementation does not yet achieve the 5x performance improvement goal.")
    println(s"Current improvement: ${time1.toDouble / time3.toDouble}x (goal: 5x)")
  }
}