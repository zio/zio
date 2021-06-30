package zio

import zio.test._

object ZIOLazinessSpec extends ZIOBaseSpec {

  def assertLazy(f: (=> Nothing) => Any): UIO[TestResult] =
    UIO.succeed {
      val _ = f(throw new RuntimeException("not lazy"))
      assertCompletes
    }

  def spec: ZSpec[Environment, Failure] = suite("ZIOLazinessSpec")(
    suite("IO")(
      testM("debug")(assertLazy(IO.debug(_))),
      testM("die")(assertLazy(IO.die)),
      testM("dieMessage")(assertLazy(IO.dieMessage)),
      testM("done")(assertLazy(IO.done)),
      testM("fail")(assertLazy(IO.fail)),
      testM("failCause")(assertLazy(IO.failCause)),
      testM("fromEither")(assertLazy(IO.fromEither)),
      testM("fromFiber")(assertLazy(IO.fromFiber)),
      testM("fromOption")(assertLazy(IO.fromOption)),
      testM("fromTry")(assertLazy(IO.fromTry)),
      testM("getOrFailUnit")(assertLazy(IO.getOrFailUnit)),
      testM("interruptAs")(assertLazy(IO.interruptAs)),
      testM("left")(assertLazy(IO.left)),
      testM("lock")(assertLazy(IO.lock)),
      testM("noneOrfail")(assertLazy(IO.noneOrFail)),
      testM("noneOrfail")(assertLazy(IO.noneOrFailWith(_)(_ => ???))),
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
      testM("failCause")(assertLazy(Managed.failCause)),
      testM("fromEither")(assertLazy(Managed.fromEither)),
      testM("require")(assertLazy(Managed.require)),
      testM("succeed")(assertLazy(Managed.succeed))
    ),
    suite("RIO")(
      testM("debug")(assertLazy(RIO.debug(_))),
      testM("die")(assertLazy(RIO.die)),
      testM("dieMessage")(assertLazy(RIO.dieMessage)),
      testM("done")(assertLazy(RIO.done)),
      testM("fail")(assertLazy(RIO.fail)),
      testM("failCause")(assertLazy(RIO.failCause)),
      testM("fromEither")(assertLazy(RIO.fromEither)),
      testM("fromFiber")(assertLazy(RIO.fromFiber)),
      testM("fromTry")(assertLazy(RIO.fromTry)),
      testM("getOrFail")(assertLazy(RIO.getOrFail)),
      testM("interruptAs")(assertLazy(RIO.interruptAs)),
      testM("left")(assertLazy(RIO.left)),
      testM("lock")(assertLazy(RIO.lock)),
      testM("noneOrfail")(assertLazy(RIO.noneOrFail)),
      testM("noneOrfail")(assertLazy(RIO.noneOrFailWith(_)(_ => ???))),
      testM("provide")(assertLazy(RIO.provide)),
      testM("require")(assertLazy(RIO.require)),
      testM("right")(assertLazy(RIO.right)),
      testM("sleep")(assertLazy(RIO.sleep)),
      testM("some")(assertLazy(RIO.some)),
      testM("succeed")(assertLazy(RIO.succeed))
    ),
    suite("Task")(
      testM("debug")(assertLazy(Task.debug(_))),
      testM("die")(assertLazy(Task.die)),
      testM("dieMessage")(assertLazy(Task.dieMessage)),
      testM("done")(assertLazy(Task.done)),
      testM("fail")(assertLazy(Task.fail)),
      testM("failCause")(assertLazy(Task.failCause)),
      testM("fromEither")(assertLazy(Task.fromEither)),
      testM("fromFiber")(assertLazy(Task.fromFiber)),
      testM("fromTry")(assertLazy(Task.fromTry)),
      testM("interruptAs")(assertLazy(Task.interruptAs)),
      testM("left")(assertLazy(Task.left)),
      testM("lock")(assertLazy(Task.lock)),
      testM("noneOrfail")(assertLazy(Task.noneOrFail)),
      testM("noneOrfail")(assertLazy(Task.noneOrFailWith(_)(_ => ???))),
      testM("require")(assertLazy(Task.require)),
      testM("right")(assertLazy(Task.right)),
      testM("some")(assertLazy(Task.some)),
      testM("succeed")(assertLazy(Task.succeed))
    ),
    suite("UIO")(
      testM("debug")(assertLazy(UIO.debug(_))),
      testM("die")(assertLazy(UIO.die)),
      testM("dieMessage")(assertLazy(UIO.dieMessage)),
      testM("done")(assertLazy(UIO.done)),
      testM("failCause")(assertLazy(UIO.failCause)),
      testM("fromEither")(assertLazy(UIO.fromEither)),
      testM("fromFiber")(assertLazy(UIO.fromFiber)),
      testM("interruptAs")(assertLazy(UIO.interruptAs)),
      testM("left")(assertLazy(UIO.left)),
      testM("lock")(assertLazy(UIO.lock)),
      testM("right")(assertLazy(UIO.right)),
      testM("some")(assertLazy(UIO.some)),
      testM("succeed")(assertLazy(UIO.succeed))
    ),
    suite("URIO")(
      testM("debug")(assertLazy(URIO.debug(_))),
      testM("die")(assertLazy(URIO.die)),
      testM("dieMessage")(assertLazy(URIO.dieMessage)),
      testM("done")(assertLazy(URIO.done)),
      testM("failCause")(assertLazy(URIO.failCause)),
      testM("fromEither")(assertLazy(URIO.fromEither)),
      testM("fromFiber")(assertLazy(URIO.fromFiber)),
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
      testM("failCause")(assertLazy(ZManaged.failCause)),
      testM("fromEither")(assertLazy(ZManaged.fromEither)),
      testM("interruptAs")(assertLazy(ZManaged.interruptAs)),
      testM("require")(assertLazy(ZManaged.require)),
      testM("succeed")(assertLazy(ZManaged.succeed))
    ),
    suite("ZIO")(
      testM("debug")(assertLazy(ZIO.debug(_))),
      testM("die")(assertLazy(ZIO.die)),
      testM("dieMessage")(assertLazy(ZIO.dieMessage)),
      testM("done")(assertLazy(ZIO.done)),
      testM("fail")(assertLazy(ZIO.fail)),
      testM("failCause")(assertLazy(ZIO.failCause)),
      testM("fromEither")(assertLazy(ZIO.fromEither)),
      testM("fromFiber")(assertLazy(ZIO.fromFiber)),
      testM("fromOption")(assertLazy(ZIO.fromOption)),
      testM("fromPromiseScala")(assertLazy(ZIO.fromPromiseScala)),
      testM("fromTry")(assertLazy(ZIO.fromTry)),
      testM("getOrFailUnit")(assertLazy(ZIO.getOrFailUnit)),
      testM("interruptAs")(assertLazy(ZIO.interruptAs)),
      testM("left")(assertLazy(ZIO.left)),
      testM("lock")(assertLazy(ZIO.lock)),
      testM("noneOrfail")(assertLazy(ZIO.noneOrFail)),
      testM("noneOrfail")(assertLazy(ZIO.noneOrFailWith(_)(_ => ???))),
      testM("provide")(assertLazy(ZIO.provide)),
      testM("require")(assertLazy(ZIO.require)),
      testM("right")(assertLazy(ZIO.right)),
      testM("sleep")(assertLazy(ZIO.sleep)),
      testM("some")(assertLazy(ZIO.some)),
      testM("succeed")(assertLazy(ZIO.succeed))
    )
  )
}
