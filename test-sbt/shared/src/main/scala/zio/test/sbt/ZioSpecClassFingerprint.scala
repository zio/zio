package zio.test.sbt

import sbt.testing.SubclassFingerprint
import zio.test.ZIOSpecAbstract

object ZioSpecClassFingerprint extends SubclassFingerprint {
  def superclassName(): String        = classOf[ZIOSpecAbstract].getName
  final def isModule()                = false
  final def requireNoArgConstructor() = true
}
