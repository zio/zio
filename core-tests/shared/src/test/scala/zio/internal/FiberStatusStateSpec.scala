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
        assertTrue(state.getAsyncs() == 0) &&
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
        test("enterSuspend(false)") {
          val state = make()

          val result = state.enterSuspend(false)

          assertTrue(result == 1) &&
          assertTrue(state.getAsyncs() == 1) &&
          assertTrue(state.getInterruptible() == false)
        } +
        test("enterSuspend(true)") {
          val state = make()

          val result = state.enterSuspend(true)

          assertTrue(result == 1) &&
          assertTrue(state.getAsyncs() == 1) &&
          assertTrue(state.getInterruptible() == true)
        } +
        test("attemptResume - success") {
          val state = make()

          val result = state.enterSuspend(true)

          assertTrue(state.attemptResume(result) == true) &&
          assertTrue(state.getStatus() == Status.Running)
        } +
        test("attemptResume - failure due to wrong async") {
          val state = make()

          val result = state.enterSuspend(true)

          assertTrue(state.attemptResume(result + 1) == false) &&
          assertTrue(state.getStatus() == Status.Suspended)
        } +
        test("attemptResume - failure due to already resumed") {
          val state = make()

          val result = state.enterSuspend(true)

          state.ref.set(
            FiberStatusIndicator.withStatus(state.ref.get().asInstanceOf[FiberStatusIndicator], Status.Running)
          )

          assertTrue(state.attemptResume(result) == false)
        } +
        test("attemptAsyncInterrupt - success") {
          val state = make()

          val result = state.enterSuspend(true)

          val attempted = state.attemptAsyncInterrupt(result)

          assertTrue(attempted == true)
        } +
        test("attemptAsyncInterrupt - failure due to wrong asyncs") {
          val state = make()

          val result = state.enterSuspend(true)

          state.attemptResume(result)
          state.enterSuspend(true)

          val attempted = state.attemptAsyncInterrupt(result)

          assertTrue(attempted == false)
        } +
        test("attemptAsyncInterrupt - failure due to wrong status") {
          val state = make()

          val result = state.enterSuspend(true)

          state.ref.set(
            FiberStatusIndicator.withStatus(state.ref.get().asInstanceOf[FiberStatusIndicator], Status.Running)
          )

          val attempted = state.attemptAsyncInterrupt(result)

          assertTrue(attempted == false)
        } +
        test("attemptAsyncInterrupt - failure due to uninterruptible") {
          val state = make()

          val result = state.enterSuspend(false)

          val attempted = state.attemptAsyncInterrupt(result)

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
