/*
rule = Zio2Upgrade
 */
package fix

import zio.ZLayer

object ZLayerPieces {
  ZLayer.requires[Int]
  ZLayer.identity[Int]
}
