//> using lib "dev.zio::zio:2.0.15"
//> using lib "org.typelevel::cats-effect:3.5.1"

import zio._
import cats.effect.unsafe.implicits.global

/**
 * Standalone script to verify if the optimized race implementation solves the performance issue
 * mentioned in the bounty. This script compares:
 * 1. Original ZIO race implementation
 * 2. Optimized ZIO race implementation
 * 3. Cats-effect race implementation
 *
 * The goal is to confirm if we've successfully addressed the 5x performance gap.
 */
object VerifyRaceOptimization {
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

  def main(args: Array[String]): Unit = {
    println("=== ZIO Race Optimization Verification ===\n")
    println("Running benchmark to verify if the optimized race implementation")
    println("solves the performance issue mentioned in the bounty.\n")
    
    // Run warmup
    println("Running warmup...")
    try {
      runCatsRace()
      runOriginalZioRace()
      runOptimizedZioRace()
    } catch {
      case e: Exception => 
        println(s"Warmup error: ${e.getMessage}")
        e.printStackTrace()
    }
    
    println("\nRunning actual benchmark...")
    
    // Measure cats-effect race
    var catsTime: Long = 0
    try {
      val start1 = System.nanoTime()
      val result1 = runCatsRace()
      catsTime = System.nanoTime() - start1
      println(f"Cats-Effect race: $result1 iterations in ${catsTime/1000000.0}%.2f ms")
    } catch {
      case e: Exception => 
        println(s"Cats-effect benchmark error: ${e.getMessage}")
        e.printStackTrace()
    }
    
    // Measure original ZIO race
    var originalZioTime: Long = 0
    try {
      val start2 = System.nanoTime()
      val result2 = runOriginalZioRace()
      originalZioTime = System.nanoTime() - start2
      println(f"Original ZIO race: $result2 iterations in ${originalZioTime/1000000.0}%.2f ms")
    } catch {
      case e: Exception => 
        println(s"Original ZIO benchmark error: ${e.getMessage}")
        e.printStackTrace()
    }
    
    // Measure optimized ZIO race
    var optimizedZioTime: Long = 0
    try {
      val start3 = System.nanoTime()
      val result3 = runOptimizedZioRace()
      optimizedZioTime = System.nanoTime() - start3
      println(f"Optimized ZIO race: $result3 iterations in ${optimizedZioTime/1000000.0}%.2f ms")
    } catch {
      case e: Exception => 
        println(s"Optimized ZIO benchmark error: ${e.getMessage}")
        e.printStackTrace()
    }
    
    if (catsTime > 0 && originalZioTime > 0 && optimizedZioTime > 0) {
      // Calculate performance ratios
      val catsToOriginalRatio = catsTime.toDouble / originalZioTime.toDouble
      val catsToOptimizedRatio = catsTime.toDouble / optimizedZioTime.toDouble
      val originalToOptimizedRatio = originalZioTime.toDouble / optimizedZioTime.toDouble
      
      println("\nPerformance ratios:")
      println(f"Cats-Effect / Original ZIO = $catsToOriginalRatio%.2fx")
      println(f"Cats-Effect / Optimized ZIO = $catsToOptimizedRatio%.2fx")
      println(f"Original ZIO / Optimized ZIO = $originalToOptimizedRatio%.2fx")
      
      // Verify if the 5x performance goal has been achieved
      println("\nVerification result:")
      if (catsToOptimizedRatio >= 5.0) {
        println("✅ SUCCESS: The optimized race implementation achieves the 5x performance improvement goal!")
      } else {
        println("❌ INCOMPLETE: The optimized race implementation does not yet achieve the 5x performance improvement goal.")
        println(f"   Current improvement: ${catsToOptimizedRatio}%.2fx (goal: 5x)")
      }
    } else {
      println("\nCould not calculate performance ratios due to benchmark errors.")
    }
    
    println("\nBenchmark complete!")
  }
}

VerifyRaceOptimization.main(Array())