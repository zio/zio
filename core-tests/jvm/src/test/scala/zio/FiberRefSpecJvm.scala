package zio

import zio.FiberRefSpecUtil._
import zio.test.Assertion._
import zio.test._

import java.util.concurrent.atomic.AtomicReference

object FiberRefSpecJvm extends ZIOBaseSpec {

  def spec: Spec[Environment, TestFailure[Any], TestSuccess] = suite("FiberRefSpecJvm")(
    testM("unsafe handles behave properly if fiber specific data cannot be accessed") {
      for {
        fiberRef <- FiberRef.make(initial)
        handle   <- fiberRef.unsafeAsThreadLocal
        resRef   <- UIO(new AtomicReference(("", "", "")))

        unsafelyGetSetGet = new Runnable {
                              def run(): Unit = {
                                val v1 = handle.get()
                                handle.set(update2)
                                val v2 = handle.get()
                                handle.remove()
                                val v3 = handle.get()
                                resRef.set((v1, v2, v3))
                              }
                            }

        _      <- fiberRef.set(update1)
        thread <- UIO(new Thread(unsafelyGetSetGet))
        _      <- UIO(thread.start()).ensuring(UIO(thread.join()))

        value0                  <- fiberRef.get
        values                  <- UIO(resRef.get())
        (value1, value2, value3) = values
      } yield assert((value0, value1, value2, value3))(equalTo((update1, initial, update2, initial)))
    }
  )
}
