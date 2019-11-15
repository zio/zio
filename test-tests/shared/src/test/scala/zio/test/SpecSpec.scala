package zio.test

import zio.test.Assertion._
import zio.test.TestUtils.execute
import zio.ZManaged

object SpecSpec extends ZIOBaseSpec {

  def spec = suite("SpecSpec")(
    testM("provideManagedShared gracefully handles fiber death") {
      import zio.NeedsEnv.needsEnv
      val spec = suite("Suite1")(
        test("Test1") {
          assert(true, isTrue)
        }
      ).provideManagedShared(ZManaged.dieMessage("everybody dies"))
      for {
        _ <- execute(spec)
      } yield assertCompletes
    }
  )
}
