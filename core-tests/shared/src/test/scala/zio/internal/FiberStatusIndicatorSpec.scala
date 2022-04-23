package zio.internal

import zio.test._
import zio.ZIOBaseSpec

object FiberStatusIndicatorSpec extends ZIOBaseSpec {
  import FiberStatusIndicator._

  def spec =
    suite("FiberStatusIndicatorSpec") {
      test("initial state") {
        assertTrue(getStatus(initial) == Status.Running) &&
        assertTrue(getAsyncs(initial) == 0) &&
        assertTrue(getMessages(initial) == false) &&
        assertTrue(getInterrupting(initial) == false) &&
        assertTrue(getInterruptible(initial) == true)
      } +
        test("change status to suspended") {
          assertTrue(getStatus(withStatus(initial, Status.Suspended)) == Status.Suspended)
        } +
        test("change status to running") {
          val isSuspended = withStatus(initial, Status.Suspended)

          assertTrue(getStatus(withStatus(isSuspended, Status.Running)) == Status.Running)
        } +
        test("change status to done") {
          assertTrue(getStatus(withStatus(initial, Status.Done)) == Status.Done)
        } +
        test("change interrupting to true") {
          assertTrue(getInterrupting(withInterrupting(initial, true)) == true)
        } +
        test("change interrupting to false") {
          val isTrue = withInterrupting(initial, true)

          assertTrue(getInterrupting(withInterrupting(isTrue, false)) == false)
        } +
        test("change interruptible to true") {
          assertTrue(getInterruptible(withInterruptible(initial, true)) == true)
        } +
        test("change interruptible to false") {
          val isTrue = withInterruptible(initial, true)

          assertTrue(getInterruptible(withInterruptible(isTrue, false)) == false)
        } +
        test("increment asyncs") {
          val newIndicator =
            (1 to 100).foldLeft(initial) { case (indicator, _) =>
              withAsyncs(indicator, getAsyncs(indicator) + 1)
            }
          assertTrue(getAsyncs(newIndicator) == 100)
        } +
        test("change messages to true") {
          assertTrue(getMessages(withMessages(initial, true)) == true)
        } +
        test("change messages to false") {
          assertTrue(getMessages(withMessages(withMessages(initial, true), false)) == false)
        }
    }
}
