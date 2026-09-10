package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}
import cats.syntax.all._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Forking Fibers and Running Effects in Parallel
 *
 * The "before" side of migratecatseffect.Step5Concurrency.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step5Concurrency"
 */
object Step5Concurrency extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // start replaces .fork
      fiber1 <- IO(println("worker-1")).start
      fiber2 <- IO(println("worker-2")).start

      // cancel — only takes effect where the wrapped IO opted in via Poll
      _ <- fiber1.cancel

      // race: returns Either[A, B]
      winner <- IO.race(IO.pure("fast"), IO.pure("slow"))
      _      <- IO(println(s"Race winner: $winner"))

      // join returns Outcome[IO, Throwable, A]
      _ <- fiber2.join

      // parTraverse replaces foreachPar
      squares <- List(1, 2, 3).parTraverse(n => IO.pure(n * n))
      _       <- IO(println(s"Squares: $squares"))

      // parMapN — runs both effects in parallel, returns a tuple
      pair <- (IO.pure(42), IO.pure("hello")).parMapN((a, b) => (a, b))
      _    <- IO(println(s"parMapN: $pair"))
    } yield ()
}
