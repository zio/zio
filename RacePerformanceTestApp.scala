//> using lib "dev.zio::zio:2.0.19"
//> using lib "org.typelevel::cats-effect:3.5.2"

import zio._
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration._

object RacePerformanceTestApp extends App {
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

  println("Starting performance test...")
  println(s"Running $iterations iterations for each implementation")
  
  // Warmup
  println(s"Cats-Effect warmup time (ns): ${runCatsRace()}")
  println(s"ZIO warmup time (ns): ${runZioRace()}")
  
  // Actual test
  val catsTime = runCatsRace()
  println(s"Cats-Effect time (ns): $catsTime")
  
  val zioTime = runZioRace()
  println(s"ZIO time (ns): $zioTime")
  
  val ratio = catsTime.toDouble / zioTime.toDouble
  
  println(s"Performance ratio: Cats-Effect / ZIO = $ratio")
  println(s"ZIO is ${ratio}x faster than Cats-Effect")
}