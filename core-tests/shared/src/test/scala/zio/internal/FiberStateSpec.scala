package zio.internal

import zio._
import zio.test._
import zio.ZIOBaseSpec

object FiberStateSpec extends ZIOBaseSpec {
  import zio2.{FiberState => FiberState2}
  def newState(): FiberState2[String, Int] =
    FiberState2(ZTraceElement.empty, FiberRefs.empty)

  def spec =
    suite("FiberStateSpec") {
      suite("fiber refs") {
        test("can get default value of unset fiber ref") {
          val s = newState()

          assertTrue(s.unsafeGetFiberRef(FiberRef.interruptible) == true)
        } +
          test("can set value of fiber ref") {
            val s = newState()

            s.unsafeSetFiberRef(FiberRef.interruptible, false)

            assertTrue(s.unsafeGetFiberRef(FiberRef.interruptible) == false)
          }
      } +
        suite("get / set interruptible") {
          test("default is interruptible") {
            val s = newState()

            assertTrue(s.unsafeGetInterruptible() == true)
          } +
            test("can change to uninterruptible") {
              val s = newState()

              s.unsafeSetInterruptible(false)

              assertTrue(s.unsafeGetInterruptible() == false)
            }
        } +
        suite("attemptAsyncInterrupt") {
          test("fails in initial case because it is not suspended") {
            val s = newState()

            val attempted = s.unsafeAttemptAsyncInterrupt()

            assertTrue(attempted == false)
          } +
            test("succeeds when suspended interruptibly") {
              val s = newState()

              s.unsafeEnterSuspend()

              val attempted = s.unsafeAttemptAsyncInterrupt()

              assertTrue(attempted == true)
            } +
            test("fails when suspended uninterruptibly") {
              val s = newState()

              s.unsafeSetInterruptible(false)

              s.unsafeEnterSuspend()

              val attempted = s.unsafeAttemptAsyncInterrupt()

              assertTrue(attempted == false)
            }
        } +
        suite("attemptDone") {
          test("succeeds in initial case") {
            val s = newState()

            val attempted = s.unsafeAttemptDone(Exit.succeed(42))

            assertTrue(attempted == null && s.unsafeExitValue() == Exit.succeed(42))
          } +
            test("does not succeed if message is waiting in initial case") {
              val s = newState()

              s.unsafeAddMessage(zio2.ZIO.unit)

              val attempted = s.unsafeAttemptDone(Exit.succeed(42))

              assertTrue(attempted ne null)
            } +
            test("does not succeed if message is pending") {
              val s = newState()

              s.statusState.beginAddMessage()

              val attempted = s.unsafeAttemptDone(Exit.succeed(42))

              assertTrue(attempted ne null)
            }
        }
    }
}
