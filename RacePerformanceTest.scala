import zio._
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration._
// Import the SimplifiedOptimizedRace implementation
import zio.SimplifiedOptimizedRace

object RacePerformanceTest extends ZIOAppDefault {
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

  def runZioRace(): Long = {
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
      if (i < iterations) SimplifiedOptimizedRace.race(ZIO.never, ZIO.succeed(i + 1)).flatMap(_ => loop(i + 1))
      else ZIO.succeed(i)
    }
    
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(loop(0)).getOrThrowFiberFailure()
    }
    
    val endTime = System.nanoTime()
    endTime - startTime
  }

  def run = for {
    _ <- Console.printLine("Starting performance test...")
    _ <- Console.printLine(s"Running $iterations iterations for each implementation")
    
    // Warmup
    _ <- ZIO.attempt(runCatsRace()).debug("Cats-Effect warmup time (ns)")
    _ <- ZIO.attempt(runZioRace()).debug("ZIO warmup time (ns)")
    _ <- ZIO.attempt(runOptimizedZioRace()).debug("Optimized ZIO warmup time (ns)")
    
    // Actual test
    catsTime <- ZIO.attempt(runCatsRace()).debug("Cats-Effect time (ns)")
    zioTime <- ZIO.attempt(runZioRace()).debug("ZIO time (ns)")
    optimizedZioTime <- ZIO.attempt(runOptimizedZioRace()).debug("Optimized ZIO time (ns)")
    
    standardRatio = catsTime.toDouble / zioTime.toDouble
    optimizedRatio = catsTime.toDouble / optimizedZioTime.toDouble
    improvementRatio = zioTime.toDouble / optimizedZioTime.toDouble
    
    _ <- Console.printLine(s"Performance ratios:")
    _ <- Console.printLine(s"Cats-Effect / Standard ZIO = $standardRatio")
    _ <- Console.printLine(s"Cats-Effect / Optimized ZIO = $optimizedRatio")
    _ <- Console.printLine(s"Standard ZIO / Optimized ZIO = $improvementRatio")
    _ <- Console.printLine(s"")
    _ <- Console.printLine(s"Standard ZIO is ${standardRatio}x faster than Cats-Effect")
    _ <- Console.printLine(s"Optimized ZIO is ${optimizedRatio}x faster than Cats-Effect")
    _ <- Console.printLine(s"Optimized ZIO is ${improvementRatio}x faster than Standard ZIO")
    
    goalAchieved = optimizedRatio >= 5.0
    _ <- Console.printLine(s"")
    _ <- Console.printLine(s"5x performance goal achieved: $goalAchieved")
  } yield ExitCode.success
}