package scalaz.zio

import scalaz.zio.clock.Clock
import scalaz.zio.console.Console
import scalaz.zio.system.System

trait RTS extends CommonRTS {
  type Context = Clock with Console with System
  val Context = new Clock.Live with Console.Live with System.Live
}
