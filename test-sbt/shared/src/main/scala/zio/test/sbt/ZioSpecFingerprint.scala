package zio.test.sbt

import sbt.testing.SubclassFingerprint
import zio.test.ZIOSpecAbstract

object ZioSpecFingerprint extends SubclassFingerprint {
  val superclassName: String        = classOf[ZIOSpecAbstract].getName
  final val isModule                = true
  final val requireNoArgConstructor = false
}
