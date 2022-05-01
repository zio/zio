package zio.internal

import zio._
import zio.test._
import zio.ZIOBaseSpec

object FiberStateSpec extends ZIOBaseSpec {
  def newState(): FiberState2[String, Int] = FiberState2(FiberRefs.empty)

  def spec =
    suite("FiberStateSpec") {
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
