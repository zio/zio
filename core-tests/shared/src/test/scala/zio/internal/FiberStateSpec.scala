package zio.internal

import zio._
import zio.test._
import zio.ZIOBaseSpec

object FiberStateSpec extends ZIOBaseSpec {
  def newState(): FiberState[String, Int] =
    RuntimeFiber(FiberId.unsafeMake(Trace.empty), FiberRefs.empty)

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
      }
    }
}
