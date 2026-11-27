package zio.test.sbt

import sbt.testing.SubclassFingerprint
import zio.test.ZIOSpecAbstract

/**
 * The sbt fingerprint of ZIO Test specs: sbt runs the Scala objects (not
 * classes) that extend [[zio.test.ZIOSpecAbstract]], such as objects that
 * extend `ZIOSpecDefault`.
 */
object ZioSpecFingerprint extends SubclassFingerprint {

  /**
   * Returns `zio.test.ZIOSpecAbstract`, the name of the class that specs
   * extend.
   */
  def superclassName(): String = classOf[ZIOSpecAbstract].getName

  /** Returns `true`: specs are Scala objects. */
  final def isModule() = true

  /**
   * Returns `false`: specs are objects, so sbt doesn't require a no-argument
   * constructor.
   */
  final def requireNoArgConstructor() = false
}
