package schedule

import zio._

/**
 * Tutorial: Schedule Step by Step — Retry and Repeat Policies in ZIO
 * Concept: The step-function contract — initial, step, and Decision
 *
 * Every Schedule is a pure state machine: `initial` seeds the state and `step`
 * returns (newState, output, Decision). `Schedule.unfold` builds one from a
 * seed value and a transition function. `schedule.run` simulates steps without
 * sleeping, collecting each output into a Chunk.
 *
 * sbt "runMain schedule.StepContractExample"
 */
object StepContractExample extends App {

  val runtime = Runtime.default

  // A schedule built with unfold: emits 1, 2, 4, 8, 16, … (doubles each step)
  val doublingSchedule: Schedule[Any, Any, Long] =
    Schedule.unfold(1L)(_ * 2)

  val program: ZIO[Any, Nothing, Unit] =
    for {
      now    <- Clock.currentDateTime
      // run simulates 5 steps without sleeping
      output <- doublingSchedule.run(now, List.fill(5)(()))
      _      <- Console.printLine(s"[unfold]    Doubling schedule outputs: $output").orDie
      // recurs(5) emits step indices 0..5 — show them with run
      recOut <- Schedule.recurs(5).run(now, List.fill(6)(()))
      _      <- Console.printLine(s"[recurs(5)] Step indices: $recOut").orDie
    } yield ()

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(program).getOrThrowFiberFailure()
  }
}
