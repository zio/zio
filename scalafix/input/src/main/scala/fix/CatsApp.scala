/*
rule = Zio2Upgrade
*/
package fix

import zio.{App, Runtime, ZEnv}

trait CatsApp extends App {
  implicit val runtime: Runtime[ZEnv] = this
}
