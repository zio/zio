package zio.test

import zio.{Ref, Unsafe, ZIO}

/**
 * Constructors for [[TestReporters]].
 */
object TestReporters {

  /**
   * Creates a `TestReporters` whose stack holds only `SuiteId.global`.
   */
  val make: ZIO[Any, Nothing, TestReporters] =
    // This SuiteId should probably be passed in a more obvious way
    ZIO.succeed(TestReporters(Ref.unsafe.make(List(SuiteId.global))(Unsafe)))
}

/**
 * Tracks which suite may send its events to the printer, as a stack of suite
 * ids with the suite that holds printing control on top. The test runner's
 * output uses it to print the events of one suite at a time.
 */
case class TestReporters(reportersStack: Ref[List[SuiteId]]) {

  /**
   * Returns `true` when suite `id` holds printing control after the call. The
   * suite takes control when the stack is empty or when the head of `ancestors`
   * (its parent) is on top of the stack, and keeps it while it is on top.
   */
  def attemptToGetPrintingControl(id: SuiteId, ancestors: List[SuiteId]): ZIO[Any, Nothing, Boolean] =
    reportersStack.updateSomeAndGet {
      case Nil                                                   => List(id)
      case rs if ancestors.nonEmpty && rs.head == ancestors.head => id :: rs
    }.map(_.head == id)

  /**
   * Removes `id` from the top of the stack. Does nothing when another suite is
   * on top.
   */
  def relinquishPrintingControl(id: SuiteId): ZIO[Any, Nothing, Unit] =
    reportersStack.updateSome {
      case currentReporter :: reporters if currentReporter == id => reporters
    }

}
