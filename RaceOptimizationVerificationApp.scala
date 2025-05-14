/**
 * Application to run the race optimization verification benchmark
 * and display the results.
 */
object RaceOptimizationVerificationApp {
  def main(args: Array[String]): Unit = {
    println("=== ZIO Race Optimization Verification ===\n")
    println("Running benchmark to verify if the optimized race implementation")
    println("solves the performance issue mentioned in the bounty.\n")
    
    val benchmark = new RaceOptimizationVerificationBenchmark()
    
    // Set iterations to 10000 for a more accurate measurement
    benchmark.iterations = 10000
    
    // Run warmup
    println("Running warmup...")
    benchmark.catsEffectRace()
    benchmark.originalZioRace()
    benchmark.optimizedZioRace()
    
    println("\nRunning actual benchmark...")
    
    // Measure cats-effect race
    val start1 = System.nanoTime()
    val result1 = benchmark.catsEffectRace()
    val time1 = System.nanoTime() - start1
    println(s"Cats-Effect race: $result1 iterations in ${time1/1000000.0} ms")
    
    // Measure original ZIO race
    val start2 = System.nanoTime()
    val result2 = benchmark.originalZioRace()
    val time2 = System.nanoTime() - start2
    println(s"Original ZIO race: $result2 iterations in ${time2/1000000.0} ms")
    
    // Measure optimized ZIO race
    val start3 = System.nanoTime()
    val result3 = benchmark.optimizedZioRace()
    val time3 = System.nanoTime() - start3
    println(s"Optimized ZIO race: $result3 iterations in ${time3/1000000.0} ms")
    
    // Calculate performance ratios
    val catsToOriginalRatio = time1.toDouble / time2.toDouble
    val catsToOptimizedRatio = time1.toDouble / time3.toDouble
    val originalToOptimizedRatio = time2.toDouble / time3.toDouble
    
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
    
    println("\nBenchmark complete!")
  }
}