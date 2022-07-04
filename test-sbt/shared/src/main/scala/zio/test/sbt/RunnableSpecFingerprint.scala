package zio.test.sbt

import sbt.testing.SubclassFingerprint
import zio.test.AbstractRunnableSpec

object RunnableSpecFingerprint extends SubclassFingerprint {
  override def superclassName(): String  = classOf[AbstractRunnableSpec].getName
  override def isModule()                = true
  override def requireNoArgConstructor() = false
}
