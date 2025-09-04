package fix

import zio._
import zio.Random

object RandomService {
  val random: URIO[Random, _] = ZIO.service[Random]
}