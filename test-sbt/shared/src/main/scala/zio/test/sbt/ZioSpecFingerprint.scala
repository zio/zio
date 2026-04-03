package zio.test.sbt

import sbt.testing.SubclassFingerprint
import zio.test.ZIOSpecAbstract

object ZioSpecFingerprint extends SubclassFingerprint {
  def superclassName(): String        = classOf[ZIOSpecAbstract].getName
  final def isModule()                = true
  final def requireNoArgConstructor() = false
}
