package zio

import zio.test._

object ZIOLazinessSpec extends ZIOBaseSpec {

  def assertLazy[A, B](f: (=> A) => B): UIO[TestResult] =
    UIO.effectTotal {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec = suite("ZIOLazinessSpec")(
    suite("Managed")(
      testM("die")(assertLazy(Managed.die)),
      testM("dieMessage")(assertLazy(Managed.dieMessage)),
      testM("done")(assertLazy(Managed.done)),
      testM("fail")(assertLazy(Managed.fail)),
      testM("fromEither")(assertLazy(Managed.fromEither)),
      testM("halt")(assertLazy(Managed.halt)),
      testM("require")(assertLazy(Managed.require)),
      testM("succeed")(assertLazy(Managed.succeed))
    ),
    suite("ZManaged")(
      testM("die")(assertLazy(ZManaged.die)),
      testM("dieMessage")(assertLazy(ZManaged.dieMessage)),
      testM("done")(assertLazy(ZManaged.done)),
      testM("fail")(assertLazy(ZManaged.fail)),
      testM("fromEither")(assertLazy(ZManaged.fromEither)),
      testM("halt")(assertLazy(ZManaged.halt)),
      testM("interruptAs")(assertLazy(ZManaged.interruptAs)),
      testM("require")(assertLazy(ZManaged.require)),
      testM("succeed")(assertLazy(ZManaged.succeed))
    )
  )
}
