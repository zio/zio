package migratefrommonix

import zio._
import zio.stream._

/**
 * Guide: Migrate from Monix to ZIO
 * Complete example combining all migration patterns:
 *   1. ZIOAppDefault entry point
 *   2. ZIO.attempt / ZIO.succeed effect constructors
 *   3. Typed error channel with mapError / catchAll
 *   4. ZIO.acquireRelease + ZIO.scoped resource management
 *   5. fork / interrupt / raceEither / collectAllPar / withParallelism
 *   6. Ref / FiberRef shared state
 *   7. Queue / Semaphore / Hub concurrent data structures
 *   8. ZStream streaming with runCollect
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.CompleteExample"
 */

// ── Domain types ───────────────────────────────────────────────────
case class Connection(id: Int) {
  def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
  def close: UIO[Unit]                 = ZIO.succeed(println(s"[cleanup] Closing connection $id"))
}

sealed trait MigrateError extends Throwable { def msg: String }
case class DbError(msg: String)      extends MigrateError
case class TimeoutError(msg: String) extends MigrateError

object CompleteExample extends ZIOAppDefault {

  // ── Resource ──────────────────────────────────────────────────────
  def makeConnection(id: Int): ZIO[Scope, Throwable, Connection] =
    ZIO.acquireRelease(
      ZIO.attempt { println(s"[acquire] Opening connection $id"); Connection(id) }
    )(_.close)

  // ── Worker: resource + typed errors + Ref + Promise ───────────────
  def worker(
    id: Int,
    counter: Ref[Int],
    done: Promise[Nothing, String]
  ): Task[Unit] =
    ZIO.scoped {
      for {
        conn   <- makeConnection(id)
        result <- conn
                    .query("SELECT 1")
                    .mapError(e => DbError(e.getMessage))
        n      <- counter.updateAndGet(_ + 1)
        _      <- ZIO.succeed(println(s"[worker-$id] got: $result, total: $n"))
        _      <- ZIO.when(n >= 2)(done.succeed(s"worker-$id finished last").unit)
      } yield ()
    }

  // ── Application ───────────────────────────────────────────────────
  def run: Task[Unit] =
    for {
      // Shared state
      counter <- Ref.make(0)
      done    <- Promise.make[Nothing, String]

      // Concurrent workers (fork replaces .start)
      fiber1 <- worker(1, counter, done).fork
      fiber2 <- worker(2, counter, done).fork

      // raceEither replaces Task.race — returns Either[A, B]
      result <- done.await.raceEither(ZIO.sleep(5.seconds).as("timeout"))
      msg    <- result match {
                  case Left(doneMsg) => ZIO.succeed(doneMsg)
                  case Right(_) =>
                    fiber1.interrupt *> fiber2.interrupt *>
                      ZIO.fail(TimeoutError("workers timed out"))
                }

      _ <- ZIO.succeed(println(s"[main] $msg"))
      _ <- fiber1.join
      _ <- fiber2.join

      // collectAllPar — replace parSequence
      squares <- ZIO.collectAllPar(List(1, 2, 3, 4).map(n => ZIO.succeed(n * n)))
      _       <- ZIO.succeed(println(s"[main] squares: $squares"))

      // withParallelism — replace parSequenceN
      bounded <- ZIO.collectAllPar(List(1, 2, 3).map(ZIO.succeed(_))).withParallelism(2)
      _       <- ZIO.succeed(println(s"[main] bounded: $bounded"))

      // ZStream — replace Observable
      stream <- ZStream
                  .fromIterable(1 to 10)
                  .filter(_ % 2 == 0)
                  .map(_ * 3)
                  .take(4)
                  .runCollect
      _      <- ZIO.succeed(println(s"[main] stream: ${stream.toList}"))

      // Hub — replace ConcurrentChannel
      _ <- ZIO.scoped {
             for {
               hub <- Hub.bounded[String](4)
               sub <- hub.subscribe
               _   <- hub.publish("broadcast message")
               m   <- sub.take
               _   <- ZIO.succeed(println(s"[main] hub: $m"))
             } yield ()
           }
    } yield ()
}
