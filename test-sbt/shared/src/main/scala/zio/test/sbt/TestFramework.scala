package zio.test.sbt

import sbt.testing.{Fingerprint, Framework}

/**
 * The sbt test framework definition that the platform versions of
 * [[ZTestFramework]] share. Its name is ZIO Test and its only fingerprint is
 * [[ZioSpecFingerprint]].
 */
trait TestFramework extends Framework {

  /** Returns the framework name, ZIO Test, underlined with ANSI codes. */
  final override def name(): String = s"${Console.UNDERLINED}ZIO Test${Console.RESET}"

  /** Returns [[ZioSpecFingerprint]] as the only fingerprint. */
  final override def fingerprints(): Array[Fingerprint] = Array(ZioSpecFingerprint)

  /**
   * Returns a new runner for one sbt test run.
   *
   * @param args
   *   the test arguments, such as `-t` and `-tags`
   * @param remoteArgs
   *   the arguments for remote runners
   * @param testClassLoader
   *   the class loader that loads the spec objects
   */
  override def runner(
    args: Array[String],
    remoteArgs: Array[String],
    testClassLoader: ClassLoader
  ): TestRunner

  final private[sbt] def runner(args: Array[String]): TestRunner = runner(args, Array(), getClassLoader)

  // Note: strangely, backend-specific class loader seems to not be available elsewhere in ZIO...
  /**
   * Returns the class loader that this framework uses to load specs when no
   * test class loader is given: the framework's own class loader on the JVM,
   * and `null` on Scala.js and Scala Native.
   */
  protected def getClassLoader: ClassLoader
}
