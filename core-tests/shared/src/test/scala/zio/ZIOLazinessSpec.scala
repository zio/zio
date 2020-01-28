package zio

import zio.test._

object ZIOLazinessSpec extends ZIOBaseSpec {

  def assertLazy[A, B](f: (=> A) => B): UIO[TestResult] =
    UIO.effectTotal {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec = suite("ZIOLazinessSpec")(
    suite("IO")(
      testM("die")(assertLazy(IO.die)),
      testM("dieMessage")(assertLazy(IO.dieMessage)),
      testM("done")(assertLazy(IO.done)),
      testM("fail")(assertLazy(IO.fail)),
      testM("fromEither")(assertLazy(IO.fromEither)),
      testM("fromFiber")(assertLazy(IO.fromFiber)),
      testM("fromOption")(assertLazy(IO.fromOption)),
      testM("fromTry")(assertLazy(IO.fromTry)),
      testM("halt")(assertLazy(IO.halt)),
      testM("interruptAs")(assertLazy(IO.interruptAs)),
      testM("left")(assertLazy(IO.left)),
      testM("lock")(assertLazy(IO.lock)),
      testM("require")(assertLazy(ZIO.require)),
      testM("right")(assertLazy(IO.right)),
      testM("some")(assertLazy(IO.some)),
      testM("succeed")(assertLazy(IO.succeed))
    ),
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
    suite("RIO")(
      testM("die")(assertLazy(RIO.die)),
      testM("dieMessage")(assertLazy(RIO.dieMessage)),
      testM("done")(assertLazy(RIO.done)),
      testM("fail")(assertLazy(RIO.fail)),
      testM("fromEither")(assertLazy(RIO.fromEither)),
      testM("fromFiber")(assertLazy(RIO.fromFiber)),
      testM("fromOption")(assertLazy(RIO.fromOption)),
      testM("fromTry")(assertLazy(RIO.fromTry)),
      testM("halt")(assertLazy(RIO.halt)),
      testM("interruptAs")(assertLazy(RIO.interruptAs)),
      testM("left")(assertLazy(RIO.left)),
      testM("lock")(assertLazy(RIO.lock)),
      testM("provide")(assertLazy(RIO.provide)),
      testM("require")(assertLazy(RIO.require)),
      testM("right")(assertLazy(RIO.right)),
      testM("sleep")(assertLazy(RIO.sleep)),
      testM("some")(assertLazy(RIO.some)),
      testM("succeed")(assertLazy(RIO.succeed))
    ),
    suite("Task")(
      testM("die")(assertLazy(Task.die)),
      testM("dieMessage")(assertLazy(Task.dieMessage)),
      testM("done")(assertLazy(Task.done)),
      testM("fail")(assertLazy(Task.fail)),
      testM("fromEither")(assertLazy(Task.fromEither)),
      testM("fromFiber")(assertLazy(Task.fromFiber)),
      testM("fromTry")(assertLazy(Task.fromTry)),
      testM("halt")(assertLazy(Task.halt)),
      testM("interruptAs")(assertLazy(Task.interruptAs)),
      testM("left")(assertLazy(Task.left)),
      testM("lock")(assertLazy(Task.lock)),
      testM("require")(assertLazy(Task.require)),
      testM("right")(assertLazy(Task.right)),
      testM("some")(assertLazy(Task.some)),
      testM("succeed")(assertLazy(Task.succeed))
    ),
    suite("UIO")(
      testM("die")(assertLazy(UIO.die)),
      testM("dieMessage")(assertLazy(UIO.dieMessage)),
      testM("done")(assertLazy(UIO.done)),
      testM("fromEither")(assertLazy(UIO.fromEither)),
      testM("fromFiber")(assertLazy(UIO.fromFiber)),
      testM("halt")(assertLazy(UIO.halt)),
      testM("interruptAs")(assertLazy(UIO.interruptAs)),
      testM("left")(assertLazy(UIO.left)),
      testM("lock")(assertLazy(UIO.lock)),
      testM("right")(assertLazy(UIO.right)),
      testM("some")(assertLazy(UIO.some)),
      testM("succeed")(assertLazy(UIO.succeed))
    ),
    suite("URIO")(
      testM("die")(assertLazy(URIO.die)),
      testM("dieMessage")(assertLazy(URIO.dieMessage)),
      testM("done")(assertLazy(URIO.done)),
      testM("fromEither")(assertLazy(URIO.fromEither)),
      testM("fromFiber")(assertLazy(URIO.fromFiber)),
      testM("halt")(assertLazy(URIO.halt)),
      testM("interruptAs")(assertLazy(URIO.interruptAs)),
      testM("left")(assertLazy(URIO.left)),
      testM("lock")(assertLazy(URIO.lock)),
      testM("provide")(assertLazy(URIO.provide)),
      testM("right")(assertLazy(URIO.right)),
      testM("sleep")(assertLazy(URIO.sleep)),
      testM("some")(assertLazy(URIO.some)),
      testM("succeed")(assertLazy(URIO.succeed))
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
    ),
    suite("ZIO")(
      testM("die")(assertLazy(ZIO.die)),
      testM("dieMessage")(assertLazy(ZIO.dieMessage)),
      testM("done")(assertLazy(ZIO.done)),
      testM("fail")(assertLazy(ZIO.fail)),
      testM("fromEither")(assertLazy(ZIO.fromEither)),
      testM("fromFiber")(assertLazy(ZIO.fromFiber)),
      testM("fromOption")(assertLazy(ZIO.fromOption)),
      testM("fromTry")(assertLazy(ZIO.fromTry)),
      testM("halt")(assertLazy(ZIO.halt)),
      testM("interruptAs")(assertLazy(ZIO.interruptAs)),
      testM("left")(assertLazy(ZIO.left)),
      testM("lock")(assertLazy(ZIO.lock)),
      testM("provide")(assertLazy(ZIO.provide)),
      testM("require")(assertLazy(ZIO.require)),
      testM("right")(assertLazy(ZIO.right)),
      testM("sleep")(assertLazy(ZIO.sleep)),
      testM("some")(assertLazy(ZIO.some)),
      testM("succeed")(assertLazy(ZIO.succeed))
    )
  )
}
