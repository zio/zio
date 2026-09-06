package migratefrommonix

import zio._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Shared State (Atomic → Ref, TaskLocal → FiberRef)
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step6SharedState"
 */
object Step6SharedState extends ZIOAppDefault {
  def run: Task[Unit] =
    ZIO.scoped {
      for {
        // Ref — replace Atomic
        counter <- Ref.make(0)
        _       <- counter.update(_ + 1)
        v1      <- counter.get
        _       <- ZIO.succeed(println(s"Ref counter: $v1"))

        // FiberRef — replace TaskLocal
        requestId <- FiberRef.make("unset")
        _         <- requestId.set("req-42")
        child     <- requestId.get
                       .flatMap(v => ZIO.succeed(println(s"FiberRef value: $v")))
                       .fork
        _         <- child.join
      } yield ()
    }
}
