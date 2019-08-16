package zio.examples

import zio.ZIO
import zio.console.Console

package object test {

  def putStrLn(str: String): ZIO[Console, Nothing, Unit] = ZIO.accessM(_.console.putStrLn(str))

}
