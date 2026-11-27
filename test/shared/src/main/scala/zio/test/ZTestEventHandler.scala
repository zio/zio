package zio.test

import zio.{UIO, ZIO}

/**
 * Receives each [[ExecutionEvent]] of a test run after the
 * [[ExecutionEventSink]] has processed it. The sbt test interface uses it to
 * report test results to sbt.
 */
trait ZTestEventHandler {

  /**
   * Handles one event of the run.
   */
  def handle(event: ExecutionEvent): UIO[Unit]
}

/**
 * Predefined [[ZTestEventHandler]] values.
 */
object ZTestEventHandler {

  /**
   * A handler that ignores every event.
   */
  val silent: ZTestEventHandler = _ => ZIO.unit
}
