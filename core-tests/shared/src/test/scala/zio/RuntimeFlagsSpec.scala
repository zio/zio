package zio

import zio.test._

object RuntimeFlagsSpec extends ZIOBaseSpec {
  import RuntimeFlag._

  def spec =
    suite("RuntimeFlagsSpec") {
      test("enabled") {
        val flags =
          RuntimeFlags(Interruption, CurrentFiber)

        assertTrue(flags.enabled(Interruption)) &&
        assertTrue(flags.enabled(CurrentFiber)) &&
        assertTrue(flags.disabled(FiberRoots)) &&
        assertTrue(flags.disabled(OpLog)) &&
        assertTrue(flags.disabled(OpSupervision)) &&
        assertTrue(flags.disabled(RuntimeMetrics))
      }
    }
}
