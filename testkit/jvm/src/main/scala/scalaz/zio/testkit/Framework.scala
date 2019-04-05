package scalaz.zio.testkit

import sbt.testing.{ Fingerprint, Framework, SubclassFingerprint }

import scalaz.zio.{ Runtime, Task }

trait TestSpec extends Runtime[Any] { self: Singleton =>
  def tests: Map[String, Task[Result]]

  final def loadObject[T](name: String): T = Class.forName(s"${name}$$").getField("MODULE$").get(null).asInstanceOf[T]
}

sealed abstract class Result
case object Succeeded                      extends Result
final case class Failed(reason: Throwable) extends Result

final class TestFramework extends Framework {
  override val name = s"${Console.UNDERLINED}ZIO-Test${Console.RESET}"

  val fingerprints: Array[Fingerprint] = Array(
    new SubclassFingerprint {
      val superclassName          = classOf[TestSpec].getCanonicalName
      val isModule                = true
      val requireNoArgConstructor = false
    }
  )

  override def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): ZioTestRunner =
    ZioTestRunner(args, remoteArgs, testClassLoader)
}
