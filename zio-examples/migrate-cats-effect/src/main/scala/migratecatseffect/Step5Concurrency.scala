package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Forking Fibers and Running Effects in Parallel
 *
 * Replaces:
 *   io.start              -> zio.fork
 *   fiber.cancel          -> fiber.interrupt
 *   IO.race(a, b)         -> a.race(b)
 *   (a, b).parMapN(f)     -> a.zipWithPar(b)(f)
 *   List.parTraverse(f)   -> ZIO.foreachPar(list)(f)
 *   List.parSequence      -> ZIO.collectAllPar(list)
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step5Concurrency"
 */
object Step5Concurrency extends ZIOAppDefault {

  def run: Task[Unit] =
    for {
      // fork replaces .start; always succeeds
      fiber1 <- ZIO.succeed(println("worker-1")).fork
      fiber2 <- ZIO.succeed(println("worker-2")).fork

      // interrupt replaces .cancel; always succeeds, returns UIO[Exit[E, A]]
      _ <- fiber1.interrupt

      // race: winner's value returned directly (not Either)
      winner <- ZIO.succeed("fast").race(ZIO.succeed("slow"))
      _      <- ZIO.succeed(println(s"Race winner: $winner"))

      // join re-raises failures from the fiber
      _ <- fiber2.join

      // foreachPar replaces parTraverse
      squares <- ZIO.foreachPar(List(1, 2, 3))(n => ZIO.succeed(n * n))
      _       <- ZIO.succeed(println(s"Squares: $squares"))

      // <&> is zipPar — runs both in parallel, returns a tuple
      pair <- ZIO.succeed(42) <&> ZIO.succeed("hello")
      _    <- ZIO.succeed(println(s"zipPar: $pair"))
    } yield ()
}
