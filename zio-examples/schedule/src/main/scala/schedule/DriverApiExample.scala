package schedule

import zio._

/**
 * Tutorial: Schedule Step by Step — Retry and Repeat Policies in ZIO
 * Concept: Driving a schedule manually with the Driver API
 *
 * Every Schedule can produce a Driver — a stateful, effectful runner that
 * exposes one step at a time. `driver.next(in)` advances one step and fails
 * with None.type when the schedule is exhausted. `driver.last` retrieves the
 * most recent output. `driver.reset` restores the initial state.
 *
 * sbt "runMain schedule.DriverApiExample"
 */
object DriverApiExample extends App {

  val runtime = Runtime.default

  private def stepOrDie(driver: Schedule.Driver[_, Any, Any, Long]): ZIO[Any, Nothing, Long] =
    driver.next(()).orDieWith(_ => new RuntimeException("Schedule ended unexpectedly at this step"))

  val program: ZIO[Any, Nothing, Unit] =
    for {
      driver <- Schedule.recurs(3).driver

      // Three successful steps: next returns the step index (0, 1, 2)
      out0 <- stepOrDie(driver)
      _    <- Console.printLine(s"Step 0 → $out0").orDie

      out1 <- stepOrDie(driver)
      _    <- Console.printLine(s"Step 1 → $out1").orDie

      out2 <- stepOrDie(driver)
      _    <- Console.printLine(s"Step 2 → $out2").orDie

      // Fourth step: recurs(3) fires Done (3 < 3 = false), storing output in last
      _ <- driver
             .next(())
             .foldZIO(
               _ => driver.last.orDie.flatMap(n =>
                 Console.printLine(s"Done  → schedule exhausted; last output was $n").orDie
               ),
               n => Console.printLine(s"Unexpected success: $n").orDie
             )

      // Reset and replay from the beginning
      _    <- driver.reset
      _    <- Console.printLine("--- reset ---").orDie
      rst0 <- stepOrDie(driver)
      _    <- Console.printLine(s"After reset, Step 0 → $rst0").orDie
    } yield ()

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(program).getOrThrowFiberFailure()
  }
}
