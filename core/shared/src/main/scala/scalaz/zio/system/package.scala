package scalaz.zio

import java.time.Instant

package object system {

  /** Determine the current system time **/
  val currentTime: IO[Nothing, Instant] = IO.sync(Instant.now)

  /** Determine the current system time in milliseconds **/
  val currentTimeMillis: IO[Nothing, Long] = IO.sync(System.currentTimeMillis)

  /** Returns the current value of the running Java Virtual Machine's high-resolution time source, in nanoseconds.
   * This method can only be used to measure elapsed time and is not related to any other notion of system or wall-clock time
   */
  val nanoTime: IO[Nothing, Long] = IO.sync(System.nanoTime)

  /** Retrieve the value of an environment variable **/
  def env(variable: String): IO[Nothing, Option[String]] = IO.sync(Option(System.getenv(variable)))

  /** Retrieve the value of a system property **/
  def property(prop: String): IO[Throwable, Option[String]] = IO.syncThrowable(Option(System.getProperty(prop)))

  /** System-specific line separator **/
  val lineSeparator: IO[Nothing, String] = IO.sync(System.lineSeparator)
}
