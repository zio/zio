package zio

import java.util.concurrent.atomic.AtomicReference

import zio.FiberRefSpecUtil._
import zio.test.Assertion._
import zio.test._

object FiberRefSpecJvm
    extends ZIOBaseSpec(
      suite("FiberRefSpecJvm")(
        testM("unsafe handles properly invoke fallback functions if fiber specific data cannot be accessed") {
          for {
            fiberRef    <- FiberRef.make(initial)
            handle      <- fiberRef.unsafeHandle
            fallbackRef <- UIO(new AtomicReference(update1))
            resRef      <- UIO(new AtomicReference("" -> ""))

            unsafelyGetSetGet = new Runnable {
              def run(): Unit = {
                val v1 = handle.unsafeGet(fallbackRef.get())
                handle.unsafeSet(update2, fallbackRef.set)
                val v2 = handle.unsafeGet(fallbackRef.get())
                resRef.set(v1 -> v2)
              }
            }

            thread <- UIO(new Thread(unsafelyGetSetGet))
            _      <- UIO(thread.start()).ensuring(UIO(thread.join()))

            value0           <- fiberRef.get
            values           <- UIO(resRef.get())
            (value1, value2) = values
          } yield assert((value0, value1, value2), equalTo((initial, update1, update2)))
        }
      )
    ) {
  override def runner =
    super.runner.withPlatform(_.withFiberContextPropagation(true))
}
