package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Shared State and Cross-Fiber Signaling
 *
 * Replaces:
 *   Ref.of[IO](value)   -> Ref.make(value)
 *   Deferred[IO, A]     -> Promise[E, A]
 *   deferred.get        -> promise.await
 *   deferred.complete   -> promise.succeed
 *   (no equivalent)     -> promise.fail
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step6SharedState"
 */
object Step6SharedState extends ZIOAppDefault {

  def run: Task[Unit] =
    for {
      // Ref — same API, different constructor
      counter <- Ref.make(0)
      n1      <- counter.updateAndGet(_ + 1)
      n2      <- counter.updateAndGet(_ + 1)
      total   <- counter.get
      _       <- ZIO.succeed(println(s"Counter after 2 updates: $total (n1=$n1, n2=$n2)"))

      // Promise — replaces Deferred, adds typed error channel
      done <- Promise.make[Nothing, String]
      _    <- done.succeed("all done")
      msg  <- done.await
      _    <- ZIO.succeed(println(s"Promise resolved: $msg"))

      // Promise with typed failure — no cats-effect equivalent
      errored <- Promise.make[String, Int]
      _       <- errored.fail("something went wrong")
      result  <- errored.await.either
      _       <- ZIO.succeed(println(s"Promise failed: $result"))
    } yield ()
}
