package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Complete example combining all six migration steps:
 *   1. ZIOAppDefault entry point
 *   2. ZIO.attempt / ZIO.succeed effect constructors
 *   3. Typed error channel with mapError / catchAll
 *   4. ZIO.acquireRelease + ZIO.scoped resource management
 *   5. fork / interrupt / race / foreachPar / <&> concurrency
 *   6. Ref / Promise shared state and cross-fiber signaling
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.CompleteExample"
 */

// ── Domain types ──────────────────────────────────────────────────
case class CompleteDbConnection(id: Int) {
  def query(sql: String): Task[String] = ZIO.attempt(s"conn-$id: $sql result")
  def close: UIO[Unit]                 = ZIO.succeed(println(s"[cleanup] Closing connection $id"))
}

sealed trait CompleteAppError extends Throwable
case class CompleteDbError(msg: String)      extends CompleteAppError
case class CompleteTimeoutError(msg: String) extends CompleteAppError

object CompleteExample extends ZIOAppDefault {

  // ── Resource ─────────────────────────────────────────────────────
  def makeDbConnection(id: Int): ZIO[Scope, Nothing, CompleteDbConnection] =
    ZIO.acquireRelease(
      ZIO.succeed { println(s"[acquire] Opening connection $id"); CompleteDbConnection(id) }
    )(conn => conn.close)

  // ── Worker: resource + typed errors + Ref + Promise ──────────────
  def worker(
    id:      Int,
    counter: Ref[Int],
    done:    Promise[Nothing, String]
  ): Task[Unit] =
    ZIO.scoped {
      for {
        conn   <- makeDbConnection(id)
        result <- conn
                    .query("SELECT 1")
                    .mapError(e => CompleteDbError(e.getMessage))
        n      <- counter.updateAndGet(_ + 1)
        _      <- ZIO.succeed(println(s"[worker-$id] got: $result, total: $n"))
        _      <- ZIO.when(n >= 2)(done.succeed(s"worker-$id finished last").unit)
      } yield ()
    }

  // ── Application ───────────────────────────────────────────────────
  def run: Task[Unit] =
    for {
      counter <- Ref.make(0)
      done    <- Promise.make[Nothing, String]

      // fork replaces .start
      fiber1 <- worker(1, counter, done).fork
      fiber2 <- worker(2, counter, done).fork

      // race done.await against a 5-second timeout
      winner <- done.await.race(ZIO.sleep(5.seconds).as("timeout"))
      _      <- ZIO.succeed(println(s"[race] winner: $winner"))

      // join re-raises any fiber failures
      _ <- fiber1.join
      _ <- fiber2.join

      // foreachPar replaces parTraverse
      squares <- ZIO.foreachPar(List(1, 2, 3))(n => ZIO.succeed(n * n))
      _       <- ZIO.succeed(println(s"[parallel] squares: $squares"))

      // <&> is zipPar
      pair <- ZIO.succeed(42) <&> ZIO.succeed("hello")
      _    <- ZIO.succeed(println(s"[zipPar] pair: $pair"))
    } yield ()
}
