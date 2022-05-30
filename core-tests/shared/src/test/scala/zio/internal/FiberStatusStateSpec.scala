package zio.internal

import zio.test._
import zio.ZIOBaseSpec

object FiberStatusStateSpec extends ZIOBaseSpec {
  import FiberStatusIndicator.Status

  def make() = FiberStatusState.unsafeMake()

  def spec =
    suite("FiberStatusStateSpec") {
      test("initial") {
        val state = make()

        assertTrue(state.getStatus() == Status.Running) &&
        assertTrue(state.clearMessages() == false) &&
        assertTrue(state.getInterruptible() == true) &&
        assertTrue(state.getInterrupting() == false)
      } +
        test("beginAddMessage - success") {
          val state = make()

          val result = state.beginAddMessage()
          state.endAddMessage()

          assertTrue(result == true) &&
          assertTrue(state.clearMessages() == true)
        } +
        test("beginAddMessage - failure because already done") {
          val state = make()

          state.attemptDone()

          val result = state.beginAddMessage()

          assertTrue(result == false) &&
          assertTrue(state.clearMessages() == false)
        } +
        test("interruptible") {
          val state = make()

          state.setInterruptible(false)
          assertTrue(state.getInterruptible() == false)
        } +
        test("interrupting") {
          val state = make()

          state.setInterrupting(true)
          assertTrue(state.getInterrupting() == true)
        } +
        test("enterSuspend uninterruptible") {
          val state = make()

          state.setInterruptible(false)
          state.enterSuspend()

          assertTrue(state.getInterruptible() == false)
        } +
        test("enterSuspend interruptible") {
          val state = make()

          state.setInterruptible(true)
          state.enterSuspend()

          assertTrue(state.getInterruptible() == true)
        } +
        test("attemptResume - success") {
          val state = make()

          state.setInterruptible(true)
          state.enterSuspend()
          state.attemptResume()

          assertTrue(state.getStatus() == Status.Running)
        } +
        test("attemptResume - failure due to already resumed") {
          val state = make()

          state.setInterruptible(true)
          state.enterSuspend()

          state.ref.set(
            FiberStatusIndicator.withStatus(state.ref.get().asInstanceOf[FiberStatusIndicator], Status.Running)
          )

          assertTrue(scala.util.Try(state.attemptResume()).isFailure == true)
        } +
        test("attemptAsyncInterrupt - success") {
          val state = make()

          state.setInterruptible(true)
          state.enterSuspend()

          val attempted = state.attemptAsyncInterrupt()

          assertTrue(attempted == true)
        } +
        test("attemptAsyncInterrupt - failure due to wrong status") {
          val state = make()

          state.setInterruptible(true)
          state.enterSuspend()

          state.ref.set(
            FiberStatusIndicator.withStatus(state.ref.get().asInstanceOf[FiberStatusIndicator], Status.Running)
          )

          val attempted = state.attemptAsyncInterrupt()

          assertTrue(attempted == false)
        } +
        test("attemptAsyncInterrupt - failure due to uninterruptible") {
          val state = make()

          state.setInterruptible(false)
          state.enterSuspend()

          val attempted = state.attemptAsyncInterrupt()

          assertTrue(attempted == false)
        } +
        test("attemptDone - success") {
          val state = make()

          assertTrue(state.attemptDone() == true)
        } +
        test("attemptDone - failure due to pending messages") {
          val state = make()

          state.beginAddMessage()
          state.endAddMessage()

          assertTrue(state.attemptDone() == false)
        } +
        test("attemptDone - failure due to partial pending messages") {
          val state = make()

          state.beginAddMessage()

          assertTrue(state.attemptDone() == false)
        } +
        test("attemptDone - catastrophic failure due to already done") {
          val state = make()

          state.attemptDone()

          try {
            state.attemptDone()

            assertTrue(false)
          } catch {
            case _: AssertionError => assertTrue(true)
          }
        }
    }
}
