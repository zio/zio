package scalanative

import zio._
import zio.test._
import zio.test.TestAspect._

/** Step 4 — Run Tests on Native
  *
  * Demonstrates TestAspect.exceptNative to exclude the large-concurrency test
  * on Scala Native, keeping native test runs under the 120-second budget.
  *
  * Run with: sbt test
  */

// Job is defined in Main.scala (same package); no redefinition needed here.

object JobQueueSpec extends ZIOSpecDefault {
  def spec = suite("JobQueueSpec")(
    test("processes a short job list in order") {
      val jobs = List(Job(1, "compile"), Job(2, "test"), Job(3, "package"))
      for {
        results <- ZIO.foreach(jobs)(job => ZIO.succeed(job.name))
      } yield assertTrue(results == List("compile", "test", "package"))
    },
    test("handles a large workload concurrently") {
      // Excluded on Scala Native to keep native test runs under the 120-second timeout
      val jobs = List.fill(500)(Job(1, "work"))
      for {
        fibers  <- ZIO.foreach(jobs)(j => ZIO.succeed(j.id).fork)
        results <- ZIO.foreach(fibers)(_.join)
      } yield assertTrue(results.length == 500)
    } @@ exceptNative
  )
}
