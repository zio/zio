package zio.test.sbt

import com.github.ghik.silencer.silent
import sbt.testing.SubclassFingerprint
import zio.test.AbstractRunnableSpec

object RunnableSpecFingerprint extends SubclassFingerprint {
  @silent("deprecated")
  val superclassName: String = classOf[AbstractRunnableSpec].getName
  final val isModule                = true
  final val requireNoArgConstructor = false
}
