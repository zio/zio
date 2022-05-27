package zio.internal

import zio._
import zio.test._
import zio.ZIOBaseSpec

object FiberStateSpec extends ZIOBaseSpec {
  import zio2.{FiberState => FiberState2}
  def newState(): FiberState2[String, Int] =
    FiberState2(FiberId.unsafeMake(Trace.empty), FiberRefs.empty)

  def spec =
    suite("FiberStateSpec") {
      suite("fiber refs") {
        test("can get default value of unset fiber ref") {
          val s = newState()

          assertTrue(s.unsafeGetFiberRef(FiberRef.forkScopeOverride) == None)
        } +
          test("can set value of fiber ref") {
            val s = newState()

            s.unsafeSetFiberRef(FiberRef.forkScopeOverride, Some(null))

            assertTrue(s.unsafeGetFiberRef(FiberRef.forkScopeOverride) == Some(null))
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
        suite("attempt async interrupt") {
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
            } +
            suite("interrupt or add message") {
              test("adds a message when fiber is not suspended") {
                val s = newState()

                val msg = zio2.ZIO.succeed(42)

                val result = s.unsafeAsyncInterruptOrAddMessage(msg)

                assertTrue(result == false && s.unsafeDrainMailbox() == msg)
              } +
                test("interrupts a message when fiber is interruptibly suspended") {
                  val s = newState()

                  val msg = zio2.ZIO.succeed(42)

                  s.unsafeEnterSuspend()

                  val result = s.unsafeAsyncInterruptOrAddMessage(msg)

                  assertTrue(result == true && s.unsafeDrainMailbox() != msg)
                } +
                test("adds a message when fiber is uninterruptibly suspended") {
                  val s = newState()

                  val msg = zio2.ZIO.succeed(42)

                  s.unsafeSetInterruptible(false)
                  s.unsafeEnterSuspend()

                  val result = s.unsafeAsyncInterruptOrAddMessage(msg)

                  assertTrue(result == false && s.unsafeDrainMailbox() == msg)
                }
            }
        } +
        suite("attempt done") {
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
