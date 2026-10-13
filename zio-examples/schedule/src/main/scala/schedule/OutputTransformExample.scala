package schedule

import zio._

/**
 * Tutorial: Schedule Step by Step — Retry and Repeat Policies in ZIO
 * Concept: Transforming schedule output with map, as, and collectAll
 *
 * `map` applies a function to every output value without changing when the
 * schedule continues. `as` replaces every output with a constant. `collectAll`
 * gathers all per-step outputs into a single Chunk returned at the end.
 *
 * sbt "runMain schedule.OutputTransformExample"
 */
object OutputTransformExample extends App {

  val runtime = Runtime.default

  // map: turn the raw step index into a human-readable label
  val labeled: Schedule[Any, Any, String] =
    Schedule.recurs(3).map(n => s"attempt ${n + 1} of 4")

  // as: replace every output with Unit (useful when the index is irrelevant)
  val silentRetry: Schedule[Any, Any, Unit] =
    Schedule.recurs(5).as(())

  val program: ZIO[Any, Nothing, Unit] =
    for {
      now <- Clock.currentDateTime

      // map demo
      labels <- labeled.run(now, List.fill(4)(()))
      _      <- Console.printLine("[map] Labeled outputs:").orDie
      _      <- ZIO.foreach(labels)(l => Console.printLine(s"  $l").orDie)

      // as demo
      asOut <- silentRetry.run(now, List.fill(6)(()))
      _     <- Console.printLine(s"[as]  All outputs are Unit: ${asOut.forall(_ == ())}").orDie

      // collectAll demo: repeat an effect and collect all step indices
      chunk <- ZIO.unit.repeat(Schedule.recurs(5).collectAll)
      _     <- Console.printLine(s"[collectAll] Collected chunk: $chunk").orDie
    } yield ()

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(program).getOrThrowFiberFailure()
  }
}
