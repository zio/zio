import java.util.concurrent.TimeUnit

import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.random.Random

object MyApp extends App {

  def run(args: List[String]): ZIO[Environment, Nothing, Int] =
    myAppLogic.as(0)

  val myAppLogic: ZIO[Clock with Console with Random, Nothing, Any] =
    for {
      start <- clock.currentTime(TimeUnit.MILLISECONDS)
      _     <- elapsed(start).repeat((ZSchedule.fixed(1.second) && ZSchedule.fixed(1.second)) && ZSchedule.recurs(6))
    } yield ()

  def elapsed(start: Long): ZIO[Clock with Console, Nothing, Unit] =
    for {
      time       <- clock.currentTime(TimeUnit.MILLISECONDS)
      difference = (time - start) / 1000.0
      _          <- console.putStrLn(difference.toString)
    } yield ()
}
