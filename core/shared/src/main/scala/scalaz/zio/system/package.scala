package scalaz.zio

import java.time.Instant

package object system {

  /** Determine the current system time **/
  val currentTime: UIO[Instant] = IO.sync(Instant.now)

  /** Determine the current system time in milliseconds **/
  val currentTimeMillis: UIO[Long] = IO.sync(System.currentTimeMillis)

  /** Returns the current value of the running Java Virtual Machine's high-resolution time source, in nanoseconds.
   * This method can only be used to measure elapsed time and is not related to any other notion of system or wall-clock time
   */
  val nanoTime: UIO[Long] = IO.sync(System.nanoTime)

  /** Retrieve the value of an environment variable **/
  def env(variable: String): UIO[Option[String]] = IO.sync(Option(System.getenv(variable)))

  /** Retrieve the value of a system property **/
  def property(prop: String): Task[Option[String]] = Task.syncThrowable(Option(System.getProperty(prop)))

  /** System-specific line separator **/
  val lineSeparator: UIO[String] = IO.sync(System.lineSeparator)
}
