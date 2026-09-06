package migratecatseffect.catseffect

import cats.effect.{Deferred, IO, IOApp, IOLocal, Ref}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Shared State and Cross-Fiber Signaling
 *
 * The "before" side of migratecatseffect.Step6SharedState, including the
 * IOLocal -> FiberRef subsection.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step6SharedState"
 */
object Step6SharedState extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // Ref — same API ZIO uses, different constructor
      counter <- Ref.of[IO, Int](0)
      n1      <- counter.updateAndGet(_ + 1)
      n2      <- counter.updateAndGet(_ + 1)
      total   <- counter.get
      _       <- IO(println(s"Counter after 2 updates: $total (n1=$n1, n2=$n2)"))

      // Deferred — completed once with a success value only
      done <- Deferred[IO, String]
      _    <- done.complete("all done")
      msg  <- done.get
      _    <- IO(println(s"Deferred resolved: $msg"))

      // No typed failure channel — Deferred can only carry a success value,
      // so a domain failure has to be smuggled through as data
      errored <- Deferred[IO, Either[String, Int]]
      _       <- errored.complete(Left("something went wrong"))
      result  <- errored.get
      _       <- IO(println(s"Deferred (smuggled failure): $result"))

      // IOLocal — fiber-local state, inherited by children at fork time
      requestId <- IOLocal("unset")
      _         <- requestId.set("req-42")
      child     <- requestId.get.flatMap(v => IO(println(s"child sees $v"))).start
      _         <- child.join
      _         <- requestId.get.flatMap(v => IO(println(s"parent still sees $v")))
    } yield ()
}
