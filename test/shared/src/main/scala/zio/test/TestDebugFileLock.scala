package zio.test

import zio.{Ref, ZIO}

private[test] object TestDebugFileLock {

  /**
   * Creates a new lock.
   */
  val make: ZIO[Any, Nothing, TestDebugFileLock] =
    Ref.Synchronized.make[Unit](()).map(TestDebugFileLock(_))
}

/**
 * A lock for the test debug files: actions passed to `updateFile` on the same
 * lock run one at a time.
 */
private[test] case class TestDebugFileLock(lock: Ref.Synchronized[Unit]) {

  /**
   * Runs `action` while holding the lock.
   */
  def updateFile(action: ZIO[Any, Nothing, Unit]) =
    lock.updateZIO(_ => action)
}
