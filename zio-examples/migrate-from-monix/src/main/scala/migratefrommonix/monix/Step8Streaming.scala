package migratefrommonix.monix

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}
import monix.reactive.Observable

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Streaming — Observable to ZStream
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.monix.Step8Streaming"
 */
object Step8Streaming extends TaskApp {
  def run(args: List[String]): Task[ExitCode] =
    for {
      // Core pipeline: fromIterable, filter, map, take, toListL
      nums <- Observable
                .fromIterable(1 to 100)
                .filter(_ % 2 == 0)
                .map(_ * 3)
                .take(5)
                .toListL

      // foldLeft
      sum <- Observable
               .fromIterable(1 to 10)
               .foldLeft(0)(_ + _)
               .firstL

      // mergeMap (concurrent flatMap)
      merged <- Observable
                  .fromIterable(1 to 3)
                  .mergeMap(n => Observable.fromIterable(List(n, n * 10)))
                  .toListL

      _ <- Task.eval(println(s"nums=$nums sum=$sum merged=$merged"))
    } yield ExitCode.Success
}
