package zio

import zio._
import zio.test._
import zio.test.Assertion._

/**
 * Test suite to verify if the optimized race implementation solves the performance issue
 * mentioned in the bounty. This test compares:
 * 1. Original ZIO race implementation
 * 2. Optimized ZIO race implementation
 * 3. Cats-effect race implementation
 *
 * The goal is to confirm if we've successfully addressed the 5x performance gap.
 */
object RaceOptimizationVerificationTest extends ZIOSpecDefault {
  val iterations = 10000

  def runCatsRace(): Long = {
    import cats.effect.IO
    
    val startTime = System.nanoTime()
    
    def loop(i: Int): IO[Int] =
      if (i < iterations) IO.race(IO.never, IO.delay(i + 1)).flatMap(_ => loop(i + 1))
      else IO.pure(i)
    
    loop(0).unsafeRunSync()
    
    val endTime = System.nanoTime()
    endTime - startTime
  }

  def runOriginalZioRace(): Long = {
    val startTime = System.nanoTime()
    
    def loop(i: Int): UIO[Int] =
      if (i < iterations) ZIO.never.race(ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = System.nanoTime()
    endTime - startTime
  }

  def runOptimizedZioRace(): Long = {
    val startTime = System.nanoTime()
    
    def loop(i: Int): UIO[Int] = {
      implicit val trace = Trace.empty
      if (i < iterations) OptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    }
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = System.nanoTime()
    endTime - startTime
  }

  def spec = suite("RaceOptimizationVerificationTest")(    
    test("optimized race should be significantly faster than cats-effect") {
      for {
        // Warmup
        _ <- ZIO.attempt(runCatsRace()).debug("Cats-Effect warmup time (ns)")
        _ <- ZIO.attempt(runOriginalZioRace()).debug("Original ZIO warmup time (ns)")
        _ <- ZIO.attempt(runOptimizedZioRace()).debug("Optimized ZIO warmup time (ns)")
        
        // Actual test
        catsTime <- ZIO.attempt(runCatsRace()).debug("Cats-Effect time (ns)")
        originalZioTime <- ZIO.attempt(runOriginalZioRace()).debug("Original ZIO time (ns)")
        optimizedZioTime <- ZIO.attempt(runOptimizedZioRace()).debug("Optimized ZIO time (ns)")
        
        catsToOriginalRatio = catsTime.toDouble / originalZioTime.toDouble
        catsToOptimizedRatio = catsTime.toDouble / optimizedZioTime.toDouble
        originalToOptimizedRatio = originalZioTime.toDouble / optimizedZioTime.toDouble
        
        _ <- Console.printLine(s"Performance ratios:")
        _ <- Console.printLine(f"Cats-Effect / Original ZIO = $catsToOriginalRatio%.2f")
        _ <- Console.printLine(f"Cats-Effect / Optimized ZIO = $catsToOptimizedRatio%.2f")
        _ <- Console.printLine(f"Original ZIO / Optimized ZIO = $originalToOptimizedRatio%.2f")
        
        // Verify that the optimized implementation meets the 5x performance goal
        _ <- if (catsToOptimizedRatio >= 5.0) {
          Console.printLine("\nVerification SUCCESSFUL: The optimized race implementation achieves the 5x performance improvement goal!")
        } else {
          Console.printLine("\nVerification INCOMPLETE: The optimized race implementation does not yet achieve the 5x performance improvement goal.")
            .zipRight(Console.printLine(f"Current improvement: ${catsToOptimizedRatio}%.2fx (goal: 5x)"))
        }
      } yield {
        assert(catsToOptimizedRatio)(isGreaterThanEqualTo(5.0)) &&
        assert(originalToOptimizedRatio)(isGreaterThan(1.0))
      }
    }
  )
}