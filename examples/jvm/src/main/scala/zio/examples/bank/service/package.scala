package zio.examples.bank

import zio.examples.bank.effect.Logger
import zio.{ URIO, ZIO }

package object service {

  def info(msg: String): URIO[Logger, Unit] = ZIO.accessM(_.log.info(msg))

  def error(msg: String): URIO[Logger, Unit] = ZIO.accessM(_.log.error(msg))

}
