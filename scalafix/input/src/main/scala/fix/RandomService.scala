/*
rule = Zio2Upgrade
*/
package fix

import zio.random.Random
import zio._

object RandomService {
  val random: URIO[Has[Random.Service], Random.Service] = ZIO.service[Random.Service]
}