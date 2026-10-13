package schedule

import zio._

/**
 * Tutorial: Schedule Step by Step — Retry and Repeat Policies in ZIO
 * Concept: Composing two schedules with &&, ||, and ++
 *
 * `&&` (intersection): both must say Continue; takes the later interval; output
 * is a tuple. `||` (union): either can say Continue; takes the earlier interval.
 * `++` (andThen): runs first schedule to Done, then hands off to the second.
 *
 * sbt "runMain schedule.CompositionExample"
 */
object CompositionExample extends App {

  val runtime = Runtime.default

  // && — intersection: exponential backoff capped at 2 additional retries
  val cappedBackoff: Schedule[Any, Any, (Duration, Long)] =
    Schedule.exponential(1.minute) && Schedule.recurs(2)

  // || — union: exponential OR at most every 30 seconds (whichever fires sooner)
  val boundedExp: Schedule[Any, Any, (Duration, Long)] =
    Schedule.exponential(500.millis) || Schedule.spaced(30.seconds)

  // ++ — sequencing: 2 immediate steps then 3 more steps
  val twoFastThenThreeSlow: Schedule[Any, Any, Long] =
    Schedule.recurs(2) ++ Schedule.recurs(3)

  val program: ZIO[Any, Nothing, Unit] =
    for {
      now <- Clock.currentDateTime

      // && demo
      andAndOut <- cappedBackoff.run(now, 1 to 10)
      _ <- Console.printLine("[&&] Exponential capped at recurs(2):").orDie
      _ <- ZIO.foreach(andAndOut) { case (d, n) =>
             Console.printLine(s"  step $n: delay ${d.render}").orDie
           }

      // || demo
      orOut <- boundedExp.run(now, List.fill(4)(()))
      _ <- Console.printLine(s"[||] Union first 4 outputs (expDelay, spacedIdx):").orDie
      _ <- ZIO.foreach(orOut) { case (d, n) =>
             Console.printLine(s"  step $n: delay ${d.render}").orDie
           }

      // ++ demo
      seqOut <- twoFastThenThreeSlow.run(now, 1 to 10)
      _ <- Console.printLine(
             s"[++] Outputs from recurs(2) ++ recurs(3): $seqOut"
           ).orDie
    } yield ()

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(program).getOrThrowFiberFailure()
  }
}
