package scalaz.zio

import scalaz.zio.clock.Clock
import scalaz.zio.console.Console
import scalaz.zio.system.System
import scalaz.zio.blocking.Blocking

trait RTS extends CommonRTS {
  type Context = Clock with Console with System with Blocking
  val Context = new Clock.Live with Console.Live with System.Live with Blocking.Live
}
