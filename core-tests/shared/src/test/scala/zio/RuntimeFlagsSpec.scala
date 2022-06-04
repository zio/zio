package zio

import zio.test._

object RuntimeFlagsSpec extends ZIOBaseSpec {
  import RuntimeFlag._

  def spec =
    suite("RuntimeFlagsSpec") {
      test("enabled & disabled") {
        val flags =
          RuntimeFlags(Interruption, CurrentFiber)

        assertTrue(flags.enabled(Interruption)) &&
        assertTrue(flags.enabled(CurrentFiber)) &&
        assertTrue(flags.disabled(FiberRoots)) &&
        assertTrue(flags.disabled(OpLog)) &&
        assertTrue(flags.disabled(OpSupervision)) &&
        assertTrue(flags.disabled(RuntimeMetrics))
      } +
        test("enabled patching") {
          val on = RuntimeFlags.enable(CurrentFiber) <> RuntimeFlags.enable(OpLog)

          assertTrue(on(RuntimeFlags.none).toSet == Set[RuntimeFlag](CurrentFiber, OpLog))
        } +
        test("inverse") {
          val bothOn = RuntimeFlags.enable(CurrentFiber) <> RuntimeFlags.enable(OpLog)

          val initial = RuntimeFlags(CurrentFiber, OpLog)

          assertTrue(RuntimeFlags.enable(CurrentFiber).inverse(initial) == RuntimeFlags(OpLog)) &&
          assertTrue(bothOn.inverse(initial) == RuntimeFlags.none)
        } +
        test("diff") {
          val oneOn  = RuntimeFlags(CurrentFiber)
          val bothOn = RuntimeFlags(CurrentFiber, OpLog)

          println(oneOn.diff(bothOn))
          println(RuntimeFlags.enable(OpLog))

          assertTrue(oneOn.diff(bothOn) == RuntimeFlags.enable(OpLog))

        }
    }
}
