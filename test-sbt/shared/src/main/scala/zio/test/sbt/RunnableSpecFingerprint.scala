package zio.test.sbt

import sbt.testing.SubclassFingerprint
import zio.test.AbstractRunnableSpec

object RunnableSpecFingerprint extends SubclassFingerprint {
  val superclassName: String  = classOf[AbstractRunnableSpec].getName
  val isModule                = true
  val requireNoArgConstructor = false
}
