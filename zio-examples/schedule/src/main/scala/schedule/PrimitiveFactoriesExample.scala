package schedule

import zio._

/**
 * Tutorial: Schedule Step by Step — Retry and Repeat Policies in ZIO
 * Concept: Primitive factory methods — recurs, spaced, exponential, fixed, forever
 *
 * The Schedule companion object provides ready-made schedules for the most
 * common policies. This example uses `schedule.run` to inspect what each
 * factory emits without any real sleeping.
 *
 * sbt "runMain schedule.PrimitiveFactoriesExample"
 */
object PrimitiveFactoriesExample extends App {

  val runtime = Runtime.default

  val program: ZIO[Any, Nothing, Unit] =
    for {
      now <- Clock.currentDateTime

      // recurs(n): emit step index 0..n then Done
      recOut <- Schedule.recurs(3).run(now, List.fill(4)(()))
      _      <- Console.printLine(s"[recurs(3)]          outputs: $recOut").orDie

      // forever: unbounded step index — we limit via the input list
      forOut <- Schedule.forever.run(now, List.fill(4)(()))
      _      <- Console.printLine(s"[forever, 4 inputs]  outputs: $forOut").orDie

      // exponential(base): delay doubles each step; output is the delay Duration
      expOut <- Schedule.exponential(1.minute).run(now, List.fill(5)(()))
      _      <- Console.printLine(s"[exponential(1.min)] delays:  ${expOut.map(_.render).mkString(", ")}").orDie

      // fibonacci(base): fibonacci-sequence delays
      fibOut <- Schedule.fibonacci(100.millis).run(now, List.fill(5)(()))
      _      <- Console.printLine(s"[fibonacci(100ms)]   delays:  ${fibOut.map(_.render).mkString(", ")}").orDie

      // spaced(d): fixed gap after each run; output is the step index
      spcOut <- Schedule.spaced(1.second).run(now, List.fill(3)(()))
      _      <- Console.printLine(s"[spaced(1s)]         outputs: $spcOut").orDie
    } yield ()

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(program).getOrThrowFiberFailure()
  }
}
