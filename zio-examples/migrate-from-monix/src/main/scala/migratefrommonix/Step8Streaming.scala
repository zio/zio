package migratefrommonix

import zio._
import zio.stream._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Streaming — Observable to ZStream
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step8Streaming"
 */
object Step8Streaming extends ZIOAppDefault {
  def run: Task[Unit] =
    for {
      // Core pipeline: fromIterable, filter, map, take, runCollect (returns Chunk, not List)
      nums <- ZStream
                .fromIterable(1 to 100)
                .filter(_ % 2 == 0)
                .map(_ * 3)
                .take(5)
                .runCollect

      // runFold — replace foldLeft
      sum <- ZStream
               .fromIterable(1 to 10)
               .runFold(0)(_ + _)

      // flatMapPar — replace mergeMap (with explicit concurrency)
      merged <- ZStream
                  .fromIterable(1 to 3)
                  .flatMapPar(3)(n => ZStream.fromIterable(List(n, n * 10)))
                  .runCollect

      _ <- ZIO.succeed(println(s"nums=$nums asList=${nums.toList} sum=$sum merged=$merged"))
    } yield ()
}
