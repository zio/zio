package zio

import zio.test._

object ZIOLazinessSpec extends ZIOBaseSpec {

  def assertLazy(f: (=> Nothing) => Any): UIO[Assert] =
    UIO.succeed {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec = suite("ZIOLazinessSpec")(
    suite("IO")(
      test("die")(assertLazy(IO.die)),
      test("dieMessage")(assertLazy(IO.dieMessage)),
      test("done")(assertLazy(IO.done)),
      test("fail")(assertLazy(IO.fail)),
      test("failCause")(assertLazy(IO.failCause)),
      test("fromEither")(assertLazy(IO.fromEither)),
      test("fromFiber")(assertLazy(IO.fromFiber)),
      test("fromOption")(assertLazy(IO.fromOption)),
      test("fromTry")(assertLazy(IO.fromTry)),
      test("getOrFailUnit")(assertLazy(IO.getOrFailUnit)),
      test("interruptAs")(assertLazy(IO.interruptAs)),
      test("left")(assertLazy(IO.left)),
      test("onExecutor")(assertLazy(IO.onExecutor)),
      test("right")(assertLazy(IO.right)),
      test("some")(assertLazy(IO.some)),
      test("succeed")(assertLazy(IO.succeed))
    ),
    suite("RIO")(
      test("die")(assertLazy(RIO.die)),
      test("dieMessage")(assertLazy(RIO.dieMessage)),
      test("done")(assertLazy(RIO.done)),
      test("fail")(assertLazy(RIO.fail)),
      test("failCause")(assertLazy(RIO.failCause)),
      test("fromEither")(assertLazy(RIO.fromEither)),
      test("fromFiber")(assertLazy(RIO.fromFiber)),
      test("fromTry")(assertLazy(RIO.fromTry)),
      test("getOrFail")(assertLazy(RIO.getOrFail)),
      test("interruptAs")(assertLazy(RIO.interruptAs)),
      test("left")(assertLazy(RIO.left)),
      test("onExecutor")(assertLazy(RIO.onExecutor)),
      test("provideEnvironment")(assertLazy(RIO.provideEnvironment)),
      test("right")(assertLazy(RIO.right)),
      test("sleep")(assertLazy(RIO.sleep)),
      test("some")(assertLazy(RIO.some)),
      test("succeed")(assertLazy(RIO.succeed))
    ),
    suite("Task")(
      test("die")(assertLazy(Task.die)),
      test("dieMessage")(assertLazy(Task.dieMessage)),
      test("done")(assertLazy(Task.done)),
      test("fail")(assertLazy(Task.fail)),
      test("failCause")(assertLazy(Task.failCause)),
      test("fromEither")(assertLazy(Task.fromEither)),
      test("fromFiber")(assertLazy(Task.fromFiber)),
      test("fromTry")(assertLazy(Task.fromTry)),
      test("interruptAs")(assertLazy(Task.interruptAs)),
      test("left")(assertLazy(Task.left)),
      test("onExecutor")(assertLazy(Task.onExecutor)),
      test("right")(assertLazy(Task.right)),
      test("some")(assertLazy(Task.some)),
      test("succeed")(assertLazy(Task.succeed))
    ),
    suite("UIO")(
      test("die")(assertLazy(UIO.die)),
      test("dieMessage")(assertLazy(UIO.dieMessage)),
      test("done")(assertLazy(UIO.done)),
      test("failCause")(assertLazy(UIO.failCause)),
      test("fromEither")(assertLazy(UIO.fromEither)),
      test("fromFiber")(assertLazy(UIO.fromFiber)),
      test("interruptAs")(assertLazy(UIO.interruptAs)),
      test("left")(assertLazy(UIO.left)),
      test("onExecutor")(assertLazy(UIO.onExecutor)),
      test("right")(assertLazy(UIO.right)),
      test("some")(assertLazy(UIO.some)),
      test("succeed")(assertLazy(UIO.succeed))
    ),
    suite("URIO")(
      test("die")(assertLazy(URIO.die)),
      test("dieMessage")(assertLazy(URIO.dieMessage)),
      test("done")(assertLazy(URIO.done)),
      test("failCause")(assertLazy(URIO.failCause)),
      test("fromEither")(assertLazy(URIO.fromEither)),
      test("fromFiber")(assertLazy(URIO.fromFiber)),
      test("interruptAs")(assertLazy(URIO.interruptAs)),
      test("left")(assertLazy(URIO.left)),
      test("onExecutor")(assertLazy(URIO.onExecutor)),
      test("provideEnvironment")(assertLazy(URIO.provideEnvironment)),
      test("right")(assertLazy(URIO.right)),
      test("sleep")(assertLazy(URIO.sleep)),
      test("some")(assertLazy(URIO.some)),
      test("succeed")(assertLazy(URIO.succeed))
    ),
    suite("ZIO")(
      test("die")(assertLazy(ZIO.die)),
      test("dieMessage")(assertLazy(ZIO.dieMessage)),
      test("done")(assertLazy(ZIO.done)),
      test("fail")(assertLazy(ZIO.fail)),
      test("failCause")(assertLazy(ZIO.failCause)),
      test("fromEither")(assertLazy(ZIO.fromEither)),
      test("fromFiber")(assertLazy(ZIO.fromFiber)),
      test("fromOption")(assertLazy(ZIO.fromOption)),
      test("fromTry")(assertLazy(ZIO.fromTry)),
      test("getOrFailUnit")(assertLazy(ZIO.getOrFailUnit)),
      test("interruptAs")(assertLazy(ZIO.interruptAs)),
      test("left")(assertLazy(ZIO.left)),
      test("onExecutor")(assertLazy(ZIO.onExecutor)),
      test("provideEnvironment")(assertLazy(ZIO.provideEnvironment)),
      test("right")(assertLazy(ZIO.right)),
      test("sleep")(assertLazy(ZIO.sleep)),
      test("some")(assertLazy(ZIO.some)),
      test("succeed")(assertLazy(ZIO.succeed))
    )
  )
}
