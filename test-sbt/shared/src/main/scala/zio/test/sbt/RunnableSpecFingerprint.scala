package zio.test.sbt

import sbt.testing.SubclassFingerprint
import zio.test.AbstractRunnableSpec

object RunnableSpecFingerprint extends SubclassFingerprint {
  val superclassName: String        = classOf[AbstractRunnableSpec].getName
  final val isModule                = true
  final val requireNoArgConstructor = false
}
