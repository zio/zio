import java.time.{ LocalDateTime }

import zio.clock.Clock
import zio.console.{ putStrLn, Console }
import zio.duration._
import zio.{ Schedule, Task, ZIO }

object BrokenUnsafeRunToFutureReproducer extends zio.App {
  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val effect = ZIO
      .effectSuspendTotal(putStrLn(s"Current time is ${LocalDateTime.now}"))
      .delay(1.second)

    val toFutureAndBackToEffect = for {
      rt <- ZIO.runtime[Console with Clock]
      _  <- Task.fromFuture(_ => rt.unsafeRunToFuture(effect))
    } yield ()

    toFutureAndBackToEffect
      .repeat(Schedule.fixed(1.second))
      .orDie
      .as(0)
  }
}
