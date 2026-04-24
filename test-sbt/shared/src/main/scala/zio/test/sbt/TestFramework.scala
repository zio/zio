package zio.test.sbt

import sbt.testing.{Fingerprint, Framework}

trait TestFramework extends Framework {
  final override def name(): String = s"${Console.UNDERLINED}ZIO Test${Console.RESET}"

  final override def fingerprints(): Array[Fingerprint] = Array(ZioSpecFingerprint)

  override def runner(
    args: Array[String],
    remoteArgs: Array[String],
    testClassLoader: ClassLoader
  ): TestRunner

  final private[sbt] def runner(args: Array[String]): TestRunner = runner(args, Array(), getClassLoader)

  // Note: strangely, backend-specific class loader seems to not be available elsewhere in ZIO...
  protected def getClassLoader: ClassLoader
}
