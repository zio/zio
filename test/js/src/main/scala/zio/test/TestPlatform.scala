package zio.test

/**
 * `TestPlatform` provides information about the platform tests are being run
 * on to enable platform specific test configuration.
 */
object TestPlatform {

  /**
   * Returns whether the current platform is ScalaJS.
   */
  val isJS = true

  /**
   * Returns whether the currently platform is the JVM.
   */
  val isJVM = false
}
