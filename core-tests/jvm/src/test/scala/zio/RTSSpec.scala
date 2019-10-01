package zio

import zio.clock.Clock
import zio.duration._
import zio.test._
import zio.test.Assertion._

object RTSSpec
    extends ZIOBaseSpec(
      suite("Blocking specs (to be migrated to ZIOSpecJvm)")(
        testM("blocking caches threads") {
          import zio.blocking.Blocking

          def runAndTrack(ref: Ref[Set[Thread]]): ZIO[Blocking with Clock, Nothing, Boolean] =
            blocking.blocking {
              UIO(Thread.currentThread())
                .flatMap(thread => ref.modify(set => (set.contains(thread), set + thread))) <* ZIO
                .sleep(1.millis)
            }

          val io =
            for {
              accum <- Ref.make(Set.empty[Thread])
              b     <- runAndTrack(accum).repeat(Schedule.doUntil[Boolean](_ == true))
            } yield b

          val env = new Clock.Live with Blocking.Live

          assertM(io.provide(env), isTrue)
        },
        testM("blocking IO is effect blocking") {
          for {
            done  <- Ref.make(false)
            start <- IO.succeed(internal.OneShot.make[Unit])
            fiber <- blocking.effectBlocking { start.set(()); Thread.sleep(60L * 60L * 1000L) }
                      .ensuring(done.set(true))
                      .fork
            _     <- IO.succeed(start.get())
            res   <- fiber.interrupt
            value <- done.get
          } yield assert(res, isInterrupted) && assert(value, isTrue)
        }
      )
    )
