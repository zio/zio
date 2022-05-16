package zio.internal

import zio._
import zio.test._
import zio.ZIOBaseSpec

object FiberStateSpec extends ZIOBaseSpec {
  def newState(): FiberState2[String, Int] =
    FiberState2(ZTraceElement.empty, FiberRefs.empty)

  def spec =
    suite("FiberStateSpec") {
      suite("fiber refs") {
        test("can get default value of unset fiber ref") {
          val s = newState()

          assertTrue(s.getFiberRef(FiberRef.interruptible) == true)
        } +
          test("can set value of fiber ref") {
            val s = newState()

            s.setFiberRef(FiberRef.interruptible, false)

            assertTrue(s.getFiberRef(FiberRef.interruptible) == false)
          }
      } +
        suite("get / set interruptible") {
          test("default is interruptible") {
            val s = newState()

            assertTrue(s.getInterruptible() == true)
          } +
            test("can change to uninterruptible") {
              val s = newState()

              s.setInterruptible(false)

              assertTrue(s.getInterruptible() == false)
            }
        } +
        suite("attemptAsyncInterrupt") {
          test("fails in initial case because it is not suspended") {
            val s = newState()

            val attempted = s.attemptAsyncInterrupt(s.getAsyncs())

            assertTrue(attempted == false)
          } +
            test("succeeds when suspended interruptibly") {
              val s = newState()

              val asyncs = s.enterSuspend()

              val attempted = s.attemptAsyncInterrupt(asyncs)

              assertTrue(attempted == true)
            } +
            test("fails when suspended uninterruptibly") {
              val s = newState()

              s.setInterruptible(false)

              val asyncs = s.enterSuspend()

              val attempted = s.attemptAsyncInterrupt(asyncs)

              assertTrue(attempted == false)
            }
        } +
        suite("attemptDone") {
          test("succeeds in initial case") {
            val s = newState()

            val attempted = s.attemptDone(Exit.succeed(42))

            assertTrue(attempted == null && s.exitValue() == Exit.succeed(42))
          } +
            test("does not succeed if message is waiting in initial case") {
              val s = newState()

              s.addMessage(ZIO.unit)

              val attempted = s.attemptDone(Exit.succeed(42))

              assertTrue(attempted == ZIO.unit)
            } +
            test("does not succeed if message is pending") {
              val s = newState()

              s.statusState.beginAddMessage()

              val attempted = s.attemptDone(Exit.succeed(42))

              assertTrue(attempted == ZIO.unit)
            }
        }
    }
}
