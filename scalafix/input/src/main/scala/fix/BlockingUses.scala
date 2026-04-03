/*
rule = Zio2Upgrade
 */
package fix

import zio.blocking.effectBlockingIO
import zio.blocking._
import zio.blocking.Blocking

object BlockingUses {
  val x = Blocking.Service
}
