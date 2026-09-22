package migratecatseffect.catseffect

import cats.effect.{IO, IOApp, Resource}
import cats.effect.kernel.{Deferred, Ref}
import cats.syntax.all._

import scala.concurrent.duration._

/**
 * Guide: Migrate from Cats Effect to ZIO
 *
 * The "before" side of migratecatseffect.CompleteExample — the motivating
 * cats-effect program from the guide's "The Problem" section, combining
 * IOApp, Resource, typed-ish errors, Ref, Deferred, and fiber concurrency.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.CompleteExample"
 */

sealed abstract class AppError(msg: String) extends RuntimeException(msg)
case class DbError(msg: String)      extends AppError(msg)
case class TimeoutError(msg: String) extends AppError(msg)

case class DbConnection(id: Int) {
  def query(sql: String): IO[String] = IO(s"conn-$id: $sql result")
  def close(): IO[Unit] = IO(println(s"[cleanup] Closing connection $id"))
}

object CompleteExample extends IOApp.Simple {

  def makeDbConnection(id: Int): Resource[IO, DbConnection] =
    Resource.make(
      IO(println(s"[acquire] Opening connection $id")).as(DbConnection(id))
    )(conn => conn.close())

  def worker(id: Int, counter: Ref[IO, Int], done: Deferred[IO, String]): IO[Unit] =
    makeDbConnection(id).use { conn =>
      for {
        result <- conn.query("SELECT 1")
                    .handleErrorWith(e => IO.raiseError(DbError(e.getMessage)))
        n      <- counter.updateAndGet(_ + 1)
        _      <- IO(println(s"[worker-$id] got: $result, total: $n"))
        _      <- if (n >= 2) done.complete(s"worker-$id finished last").void else IO.unit
      } yield ()
    }

  def run: IO[Unit] =
    for {
      counter <- Ref.of[IO, Int](0)
      done    <- Deferred[IO, String]
      fiber1  <- worker(1, counter, done).start
      fiber2  <- worker(2, counter, done).start
      result  <- IO.race(done.get, IO.sleep(5.seconds).as("timeout"))
      msg     <- result match {
                   case Left(doneMsg)  => IO.pure(doneMsg)
                   case Right(timeout) =>
                     fiber1.cancel *> fiber2.cancel *> IO.raiseError(TimeoutError(timeout))
                 }
      _       <- IO(println(s"[race] Final: $msg"))
      _       <- fiber1.join
      _       <- fiber2.join
      results <- List(1, 2, 3).parTraverse(i => IO(i * i))
      _       <- IO(println(s"[parallel] Squares: $results"))
      pair    <- (IO(42), IO("hello")).parMapN((x, y) => (x, y))
      _       <- IO(println(s"[parMapN] pair: ${pair._1}, ${pair._2}"))
    } yield ()
}
