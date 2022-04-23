package fix

import zio.ZLayer

object ZLayerPieces {
  ZLayer.service[Int]
  ZLayer.service[Int]
}
