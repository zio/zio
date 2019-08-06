package zio.test.runner

import sbt.testing.SubclassFingerprint
import zio.test.DefaultRunnableSpec

object RunnableSpecFingerprint extends SubclassFingerprint {
  val superclassName: String  = classOf[DefaultRunnableSpec].getName
  val isModule                = true
  val requireNoArgConstructor = false
}
